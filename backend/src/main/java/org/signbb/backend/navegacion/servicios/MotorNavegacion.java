package org.signbb.backend.navegacion.servicios;

import java.util.List;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.Respuesta;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
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

        ResultadoInferencia inferencia = servicioReglas.evaluar(base, estado);
        if (inferencia.nivel() != NivelAlerta.NORMAL) {
            Nodo destino = inferencia.destino();
            return ResultadoPaso.terminal(destino, inferencia.nivel(), inferencia.reglas(),
                    grafo.obtenerCaminoCausal(destino));
        }

        Nodo siguiente = grafo.evaluarPaso(nodoActual, respuesta);
        if (grafo.esNodoTerminal(siguiente)) {
            return ResultadoPaso.terminal(siguiente, NivelAlerta.NORMAL, List.of(),
                    grafo.obtenerCaminoCausal(siguiente));
        }
        return ResultadoPaso.pregunta(siguiente);
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
