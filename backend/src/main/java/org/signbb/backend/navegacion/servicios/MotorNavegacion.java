package org.signbb.backend.navegacion.servicios;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.Respuesta;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.modelo.ResultadoInferencia;
import org.signbb.backend.reglas.servicios.ServicioReglas;
import org.springframework.stereotype.Service;

/**
 * Motor de navegación: recorrido guiado del grafo clínico con corto-circuito
 * por reglas (bucle externo del Algoritmo 3).
 *
 * <p>
 * El grafo ({@link GrafoClinico}) y la base de reglas ({@link BaseReglas})
 * son estructuras de datos separadas. El motor entrelaza ambas: recorre el
 * grafo pregunta por pregunta y, tras registrar cada respuesta, ejecuta
 * encadenamiento hacia adelante sobre la base de reglas mediante
 * {@link ServicioReglas#evaluar}. Si alguna regla dispara, corta y salta al
 * nodo terminal de mayor severidad; si no, avanza por la arista que
 * corresponde a la respuesta. Al llegar a un terminal del grafo sin alarma, el
 * desenlace es {@link NivelAlerta#NORMAL}.
 *
 * <p>
 * Como un backend REST es sin estado, el bucle {@code while} de la
 * formulación se descompone en dos operaciones: {@link #iniciar} (una vez por
 * recorrido) y {@link #paso} (una vez por respuesta del cuidador).
 *
 * <p>
 * MODIFICADO: se agrega
 * {@link #paso(GrafoClinico, BaseReglas, EstadoPaciente, Nodo, List)}
 * para selección múltiple. Resuelve cuál respuesta domina (mayor {@code peso}
 * del nodo destino) y delega en el
 * {@link #paso(GrafoClinico, BaseReglas, EstadoPaciente, Nodo, String)}
 * de un solo {@code String}, que no cambia.
 */
@Service
public class MotorNavegacion {

    /**
     * Condición de la arista de arranque que no corresponde a una pregunta real.
     */
    private static final String INICIA_NAVEGACION = "inicia_navegacion";

    private final ServicioReglas servicioReglas;

    /**
     * Inyecta el servicio de reglas (forward chaining + resolución por severidad).
     */
    public MotorNavegacion(ServicioReglas servicioReglas) {
        this.servicioReglas = servicioReglas;
    }

    /**
     * Inicia el recorrido: resuelve el nodo inicial y, si es un nodo de arranque
     * (una sola arista {@code inicia_navegacion}), auto-avanza hasta la primera
     * pregunta real. Devuelve esa pregunta, o un resultado terminal si el grafo
     * arranca ya en un nodo terminal.
     */
    public ResultadoPaso iniciar(EstadoPaciente estado, GrafoClinico grafo) {
        Nodo v = grafo.obtenerNodoInicial()
                .orElseThrow(() -> new IllegalStateException("el grafo no tiene nodo inicial"));
        v = autoAvanzar(grafo, v);
        if (grafo.esNodoTerminal(v)) {
            return ResultadoPaso.terminal(v, NivelAlerta.NORMAL, List.of(),
                    grafo.obtenerCaminoCausal(v));
        }
        return ResultadoPaso.pregunta(v);
    }

    /**
     * Ejecuta una iteración del bucle: registra la respuesta del cuidador, corre
     * el encadenamiento hacia adelante y decide. Si dispara alguna regla, corta
     * al destino de mayor severidad (con sus razones combinadas); si no, avanza
     * por el grafo; si el siguiente nodo es terminal, cierra en NORMAL.
     */
    public ResultadoPaso paso(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, String respuesta) {
        estado.registrarRespuesta(nodoActual.getId(), respuesta);
        return resolver(grafo, base, estado, nodoActual, respuesta);
    }

    /**
     * Selección múltiple: el cuidador marcó varias opciones a la vez para
     * {@code nodoActual}. Se registran TODAS como respuestas del nodo (así las
     * reglas AND que cruzan varias opciones de la misma pregunta pueden
     * activarse y el recorrido se interrumpe en el terminal más grave). Si
     * ninguna regla dispara, se avanza por la opción cuyo destino tiene mayor
     * {@code peso}; con una sola respuesta marcada el resultado es idéntico al
     * {@code paso} de un solo {@code String}.
     *
     * @throws IllegalArgumentException si {@code respuestasMarcadas} es nula o
     *                                  vacía.
     */
    public ResultadoPaso paso(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, List<String> respuestasMarcadas) {
        if (respuestasMarcadas == null || respuestasMarcadas.isEmpty()) {
            throw new IllegalArgumentException("respuestasMarcadas no puede ser vacío");
        }
        estado.registrarRespuestas(nodoActual.getId(), respuestasMarcadas);

        ResultadoInferencia inferencia = servicioReglas.evaluar(base, estado);
        if (!inferencia.esNormal()) {
            return resolverInferencia(grafo, estado, inferencia);
        }
        return avanzar(grafo, nodoActual, opcionMasGrave(grafo, nodoActual, respuestasMarcadas));
    }

    /** Evalúa las reglas tras registrar una respuesta y decide el siguiente paso. */
    private ResultadoPaso resolver(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, String respuesta) {
        ResultadoInferencia inferencia = servicioReglas.evaluar(base, estado);
        if (!inferencia.esNormal()) {
            return resolverInferencia(grafo, estado, inferencia);
        }
        return avanzar(grafo, nodoActual, respuesta);
    }

    /**
     * Resuelve un disparo de reglas. Si el destino de la regla ganadora es un
     * nodo terminal, el recorrido corta ahí (comportamiento histórico); si es
     * una pregunta, es una regla "gate" y el recorrido continúa desde ese nodo
     * (su nivel no se reporta como alerta).
     */
    private ResultadoPaso resolverInferencia(GrafoClinico grafo, EstadoPaciente estado,
            ResultadoInferencia inferencia) {
        for (ReglaClinica r : inferencia.reglas()) {
            estado.marcarReglaDisparada(r.getIdRegla());
        }
        Nodo destino = inferencia.destino();
        if (grafo.esNodoTerminal(destino)) {
            return ResultadoPaso.terminal(destino, inferencia.nivel(), inferencia.reglas(),
                    grafo.obtenerCaminoCausal(destino));
        }
        return ResultadoPaso.pregunta(destino);
    }

    /** Avanza por la arista de {@code respuesta}; cierra en NORMAL si el destino es terminal. */
    private ResultadoPaso avanzar(GrafoClinico grafo, Nodo nodoActual, String respuesta) {
        Nodo siguiente = grafo.evaluarPaso(nodoActual, respuesta);
        if (grafo.esNodoTerminal(siguiente)) {
            return ResultadoPaso.terminal(siguiente, NivelAlerta.NORMAL, List.of(),
                    grafo.obtenerCaminoCausal(siguiente));
        }
        return ResultadoPaso.pregunta(siguiente);
    }

    /**
     * Devuelve la opción marcada cuyo nodo destino tiene mayor {@code peso}
     * (la más grave). Se apoya en una cola de prioridad (max-heap por peso) con
     * desempate <b>estable</b> por el índice de la opción en la lista: a igual
     * peso gana la primera marcada (una {@code PriorityQueue} sin ese desempate
     * no garantiza el orden de inserción).
     */
    private String opcionMasGrave(GrafoClinico grafo, Nodo nodoActual,
            List<String> respuestasMarcadas) {
        if (respuestasMarcadas.size() == 1) {
            return respuestasMarcadas.get(0);
        }
        record Candidata(int indice, String condicion, int peso) {
        }
        PriorityQueue<Candidata> cola = new PriorityQueue<>(
                Comparator.comparingInt(Candidata::peso).reversed()
                        .thenComparingInt(Candidata::indice));
        for (int i = 0; i < respuestasMarcadas.size(); i++) {
            String condicion = respuestasMarcadas.get(i);
            cola.add(new Candidata(i, condicion,
                    grafo.evaluarPaso(nodoActual, condicion).getPeso()));
        }
        return cola.poll().condicion();
    }

    /**
     * Avanza automáticamente mientras el nodo tenga una única arista de arranque
     * {@code inicia_navegacion} (el nodo {@code inicio} del grafo). No registra
     * respuestas porque no corresponde a una pregunta real al cuidador.
     */
    private Nodo autoAvanzar(GrafoClinico grafo, Nodo v) {
        while (!grafo.esNodoTerminal(v)) {
            List<Respuesta> opciones = grafo.obtenerOpciones(v);
            if (opciones.size() == 1
                    && INICIA_NAVEGACION.equals(opciones.get(0).getCondicion())) {
                v = grafo.evaluarPaso(v, INICIA_NAVEGACION);
            } else {
                break;
            }
        }
        return v;
    }
}