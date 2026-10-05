package org.signbb.backend.navegacion.servicios;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Motor de navegación: recorrido guiado del grafo clínico con corto-circuito
 * por reglas (bucle externo del Algoritmo 3).
 *
 * <p>
 * El grafo ({@link GrafoClinico}) y la base de reglas ({@link BaseReglas})
 * son estructuras de datos separadas. El motor entrelaza ambas en cada
 * respuesta del cuidador:
 * <ol>
 * <li>Valida la respuesta contra la pregunta pendiente (opciones del nodo y
 * su tipo de interacción) <b>antes</b> de tocar el estado, de modo que una
 * respuesta inválida no deja hechos falsos en la memoria de trabajo.</li>
 * <li>Registra la respuesta como hecho {@code (idNodo, respuesta)} en
 * {@link EstadoPaciente}, que es la memoria de trabajo.</li>
 * <li>Ejecuta encadenamiento hacia adelante con
 * {@link ServicioReglas#evaluar}: se buscan las reglas cuya premisa en FND
 * (algún caso con todas sus condiciones presentes en la memoria) se
 * satisface y que no hayan disparado antes en el recorrido. Como las
 * condiciones solo leen respuestas y las conclusiones no agregan hechos
 * nuevos, una pasada de emparejamiento ya alcanza el punto fijo.</li>
 * <li>Si alguna regla dispara, gana la de mayor nivel. Si su destino es un
 * terminal, el recorrido corta ahí; si es una pregunta (regla
 * "compuerta"), el recorrido continúa desde ese nodo. Si ninguna dispara, se
 * avanza por la arista de la respuesta.</li>
 * </ol>
 *
 * <p>
 * El nivel de un terminal sale de su desenlace ({@code recomendacion} →
 * {@link NivelAlerta#NORMAL}, {@code consulta_medica} →
 * {@link NivelAlerta#CONSULTAR_MEDICO}, {@code urgencia} →
 * {@link NivelAlerta#URGENCIA}). Si se llegó por una regla, se reporta el
 * mayor entre el nivel de la regla y el del desenlace, para que el resultado
 * nunca sea menos grave que el nodo que se muestra.
 *
 * <p>
 * Para que ningún recorrido quede abierto indefinidamente, si el cuidador ya
 * respondió {@code maxPreguntas} preguntas (10 por defecto, propiedad
 * {@code signbb.navegacion.max-preguntas}) y el siguiente paso sería otra
 * pregunta, el recorrido cierra en {@link #LIMITE_PREGUNTAS} con nivel
 * {@link NivelAlerta#NORMAL}. Una regla o un terminal alcanzados en esa misma
 * respuesta tienen prioridad sobre el límite.
 *
 * <p>
 * {@link #retroceder} vuelve a la pregunta anterior para cambiar su
 * respuesta. En vez de deshacer a mano, reinicia la memoria de trabajo y
 * reproduce las respuestas que se conservan: así las respuestas deshechas y
 * las reglas que dispararon por ellas desaparecen, y el estado queda igual al
 * de un recorrido que nunca las hubiera dado.
 *
 * <p>
 * Como un backend REST es sin estado, el bucle {@code while} de la
 * formulación se descompone en dos operaciones: {@link #iniciar} (una vez por
 * recorrido), {@link #paso} (una vez por respuesta del cuidador) y
 * {@link #retroceder} (cuando el cuidador vuelve atrás).
 *
 * <p>
 * MODIFICADO: se agrega
 * {@link #paso(GrafoClinico, BaseReglas, EstadoPaciente, Nodo, List)}
 * para selección múltiple. Solo se admite en preguntas
 * {@code SELECCION_MULTIPLE}; si ninguna regla dispara, se avanza por la
 * opción cuyo destino tiene mayor {@code peso}.
 */
@Service
public class MotorNavegacion {

    /**
     * Condición de la arista de arranque que no corresponde a una pregunta real.
     */
    private static final String INICIA_NAVEGACION = "inicia_navegacion";

    /** Tipo de interacción que admite varias respuestas a la vez. */
    private static final String SELECCION_MULTIPLE = "SELECCION_MULTIPLE";

    /** Máximo de preguntas respondidas por defecto antes de cerrar el recorrido. */
    public static final int MAX_PREGUNTAS_POR_DEFECTO = 10;

    /**
     * Desenlace de sistema para un recorrido que alcanzó el máximo de
     * preguntas sin llegar a un terminal. No pertenece al grafo clínico.
     */
    public static final Nodo LIMITE_PREGUNTAS = new Nodo("sistema/limite_preguntas", "recomendacion",
            "Respondió el máximo de preguntas sin encontrar señales de alarma. Siga observando a su "
                    + "bebé y coméntelo en su próximo control; si aparece algún signo de alarma, "
                    + "consulte de inmediato.",
            1);

    private final ServicioReglas servicioReglas;
    private final int maxPreguntas;

    /**
     * Inyecta el servicio de reglas (forward chaining + resolución por
     * severidad) con el máximo de preguntas por defecto.
     */
    public MotorNavegacion(ServicioReglas servicioReglas) {
        this(servicioReglas, MAX_PREGUNTAS_POR_DEFECTO);
    }

    /**
     * Inyecta el servicio de reglas y el máximo de preguntas respondidas
     * ({@code signbb.navegacion.max-preguntas}).
     *
     * @throws IllegalArgumentException si {@code maxPreguntas} es menor que 1.
     */
    @Autowired
    public MotorNavegacion(ServicioReglas servicioReglas,
            @Value("${signbb.navegacion.max-preguntas:" + MAX_PREGUNTAS_POR_DEFECTO + "}") int maxPreguntas) {
        if (maxPreguntas < 1) {
            throw new IllegalArgumentException("maxPreguntas debe ser al menos 1");
        }
        this.servicioReglas = servicioReglas;
        this.maxPreguntas = maxPreguntas;
    }

    /**
     * Inicia el recorrido: resuelve el nodo inicial y, si es un nodo de arranque
     * (una sola arista {@code inicia_navegacion}), auto-avanza hasta la primera
     * pregunta real. Devuelve esa pregunta, o un resultado terminal (con el
     * nivel de su desenlace) si el grafo arranca ya en un nodo terminal.
     */
    public ResultadoPaso iniciar(EstadoPaciente estado, GrafoClinico grafo) {
        Nodo v = grafo.obtenerNodoInicial()
                .orElseThrow(() -> new IllegalStateException("el grafo no tiene nodo inicial"));
        v = autoAvanzar(grafo, v);
        if (grafo.esNodoTerminal(v)) {
            return terminal(grafo, v, nivelDelDesenlace(v), List.of());
        }
        return ResultadoPaso.pregunta(v);
    }

    /**
     * Ejecuta una iteración del bucle con una sola respuesta: la valida, la
     * registra, corre el encadenamiento hacia adelante y decide el siguiente
     * paso.
     *
     * @throws IllegalArgumentException si {@code nodoActual} no es una pregunta
     *                                  del grafo o {@code respuesta} no es una
     *                                  de sus opciones. El estado no cambia.
     */
    public ResultadoPaso paso(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, String respuesta) {
        validarRespuestas(grafo, nodoActual, Collections.singletonList(respuesta));
        estado.registrarRespuesta(nodoActual.getId(), respuesta);
        return aplicarLimite(grafo, estado, resolver(grafo, base, estado, nodoActual, respuesta));
    }

    /**
     * Selección múltiple: el cuidador marcó varias opciones a la vez para
     * {@code nodoActual}. Se registran TODAS como respuestas del nodo (así las
     * reglas que cruzan varias opciones de la misma pregunta pueden activarse
     * y el recorrido se interrumpe en el terminal más grave). Si ninguna regla
     * dispara, se avanza por la opción cuyo destino tiene mayor {@code peso};
     * con una sola respuesta marcada el resultado es idéntico al {@code paso}
     * de un solo {@code String}.
     *
     * @throws IllegalArgumentException si {@code respuestasMarcadas} es nula o
     *                                  vacía, repite opciones, contiene una
     *                                  opción que el nodo no tiene, o marca
     *                                  varias en una pregunta que no es
     *                                  {@code SELECCION_MULTIPLE}. El estado
     *                                  no cambia.
     */
    public ResultadoPaso paso(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, List<String> respuestasMarcadas) {
        validarRespuestas(grafo, nodoActual, respuestasMarcadas);
        estado.registrarRespuestas(nodoActual.getId(), respuestasMarcadas);
        return aplicarLimite(grafo, estado, resolver(grafo, base, estado, nodoActual,
                opcionMasGrave(grafo, nodoActual, respuestasMarcadas)));
    }

    /**
     * Vuelve a la última pregunta respondida para que el cuidador cambie su
     * respuesta. Funciona también con el recorrido finalizado (vuelve a la
     * pregunta que lo cerró). La memoria de trabajo se reinicia y se
     * reproducen, en orden, las respuestas anteriores a esa pregunta; así
     * desaparecen su respuesta y las reglas que disparó, y el encadenamiento
     * hacia adelante vuelve a evaluarse desde cero.
     *
     * @throws IllegalArgumentException si aún no hay ninguna respuesta.
     * @throws IllegalStateException    si al reproducir el recorrido no se
     *                                  vuelve a la misma pregunta (el
     *                                  contenido clínico cambió).
     */
    public ResultadoPaso retroceder(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado) {
        String anterior = estado.ultimaPreguntaRespondida()
                .orElseThrow(() -> new IllegalArgumentException("no hay pregunta anterior a la cual volver"));
        Map<String, List<String>> conservadas = estado.respuestasEnOrden();
        conservadas.remove(anterior);

        estado.reiniciar();
        ResultadoPaso reproducido = iniciar(estado, grafo);
        for (Map.Entry<String, List<String>> e : conservadas.entrySet()) {
            if (reproducido.finalizado() || !reproducido.preguntaActual().getId().equals(e.getKey())) {
                throw new IllegalStateException(
                        "no se pudo reproducir el recorrido hasta '" + anterior
                                + "': el contenido clínico cambió");
            }
            reproducido = paso(grafo, base, estado, reproducido.preguntaActual(), e.getValue());
        }
        if (reproducido.finalizado() || !reproducido.preguntaActual().getId().equals(anterior)) {
            throw new IllegalStateException(
                    "no se pudo reproducir el recorrido hasta '" + anterior
                            + "': el contenido clínico cambió");
        }
        return reproducido;
    }

    /**
     * Cierra el recorrido en {@link #LIMITE_PREGUNTAS} si ya se respondieron
     * {@code maxPreguntas} preguntas y {@code resultado} pide otra. Un
     * resultado ya finalizado (por regla o terminal) se devuelve sin cambios.
     * El camino causal llega hasta la pregunta que quedó sin responder.
     */
    private ResultadoPaso aplicarLimite(GrafoClinico grafo, EstadoPaciente estado, ResultadoPaso resultado) {
        if (resultado.finalizado() || estado.size() < maxPreguntas) {
            return resultado;
        }
        return ResultadoPaso.terminal(LIMITE_PREGUNTAS, NivelAlerta.NORMAL, List.of(),
                grafo.obtenerCaminoCausal(resultado.preguntaActual()));
    }

    /**
     * Comprueba que {@code respuestas} sea una respuesta legal a
     * {@code nodoActual}: el nodo es una pregunta del grafo, cada respuesta es
     * una de sus opciones sin repetirse, y solo hay varias si la pregunta es
     * {@code SELECCION_MULTIPLE}. Se ejecuta antes de registrar nada.
     */
    private static void validarRespuestas(GrafoClinico grafo, Nodo nodoActual, List<String> respuestas) {
        if (respuestas == null || respuestas.isEmpty()) {
            throw new IllegalArgumentException("debe venir al menos una respuesta");
        }
        if (nodoActual == null || !grafo.contains(nodoActual)) {
            throw new IllegalArgumentException("el nodo actual no está en el grafo");
        }
        if (grafo.esNodoTerminal(nodoActual)) {
            throw new IllegalArgumentException(
                    "el nodo '" + nodoActual.getId() + "' es terminal: no admite respuestas");
        }
        Set<String> opciones = new HashSet<>();
        for (Respuesta r : grafo.obtenerOpciones(nodoActual)) {
            opciones.add(r.getCondicion());
        }
        Set<String> vistas = new HashSet<>();
        for (String respuesta : respuestas) {
            if (!opciones.contains(respuesta)) {
                throw new IllegalArgumentException(
                        "'" + respuesta + "' no es una opción de '" + nodoActual.getId()
                                + "'; las opciones son " + opciones);
            }
            if (!vistas.add(respuesta)) {
                throw new IllegalArgumentException("la respuesta '" + respuesta + "' viene repetida");
            }
        }
        if (respuestas.size() > 1 && !SELECCION_MULTIPLE.equals(grafo.tipoInteraccion(nodoActual))) {
            throw new IllegalArgumentException(
                    "la pregunta '" + nodoActual.getId() + "' es " + grafo.tipoInteraccion(nodoActual)
                            + ": admite una sola respuesta");
        }
    }

    /**
     * Corre el encadenamiento hacia adelante sobre la memoria de trabajo ya
     * actualizada y decide: corte o compuerta si dispara alguna regla, o
     * avance por la arista de {@code respuesta} si no.
     */
    private ResultadoPaso resolver(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, String respuesta) {
        ResultadoInferencia inferencia = servicioReglas.evaluar(base, estado);
        if (!inferencia.esNormal()) {
            return resolverInferencia(grafo, estado, inferencia);
        }
        return avanzar(grafo, nodoActual, respuesta);
    }

    /**
     * Resuelve un disparo de reglas. Las reglas ganadoras se marcan como
     * disparadas para que no vuelvan a activarse en el recorrido. Si el
     * destino es un terminal, el recorrido corta ahí con el mayor entre el
     * nivel de la regla y el del desenlace; si es una pregunta, es una regla
     * "compuerta" y el recorrido continúa desde ese nodo (su nivel no se
     * reporta como alerta).
     */
    private ResultadoPaso resolverInferencia(GrafoClinico grafo, EstadoPaciente estado,
            ResultadoInferencia inferencia) {
        for (ReglaClinica r : inferencia.reglas()) {
            estado.marcarReglaDisparada(r.getIdRegla());
        }
        Nodo destino = inferencia.destino();
        if (grafo.esNodoTerminal(destino)) {
            return terminal(grafo, destino, mayor(inferencia.nivel(), nivelDelDesenlace(destino)),
                    inferencia.reglas());
        }
        return ResultadoPaso.pregunta(destino);
    }

    /**
     * Avanza por la arista de {@code respuesta}; si el destino es terminal,
     * cierra con el nivel de su desenlace.
     */
    private ResultadoPaso avanzar(GrafoClinico grafo, Nodo nodoActual, String respuesta) {
        Nodo siguiente = grafo.evaluarPaso(nodoActual, respuesta);
        if (grafo.esNodoTerminal(siguiente)) {
            return terminal(grafo, siguiente, nivelDelDesenlace(siguiente), List.of());
        }
        return ResultadoPaso.pregunta(siguiente);
    }

    /** Resultado terminal con su camino causal. */
    private static ResultadoPaso terminal(GrafoClinico grafo, Nodo destino, NivelAlerta nivel,
            List<ReglaClinica> razones) {
        return ResultadoPaso.terminal(destino, nivel, razones, grafo.obtenerCaminoCausal(destino));
    }

    /** Nivel de alerta que corresponde al desenlace de un nodo terminal. */
    static NivelAlerta nivelDelDesenlace(Nodo terminal) {
        return switch (terminal.getTipo()) {
            case "urgencia" -> NivelAlerta.URGENCIA;
            case "consulta_medica" -> NivelAlerta.CONSULTAR_MEDICO;
            default -> NivelAlerta.NORMAL;
        };
    }

    /** El más severo de dos niveles. */
    private static NivelAlerta mayor(NivelAlerta a, NivelAlerta b) {
        return a.compareTo(b) >= 0 ? a : b;
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
