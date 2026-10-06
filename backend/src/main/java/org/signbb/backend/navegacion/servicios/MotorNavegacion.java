package org.signbb.backend.navegacion.servicios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.FotoNavegacion;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.OpcionPendiente;
import org.signbb.backend.navegacion.modelo.PasoCausal;
import org.signbb.backend.navegacion.modelo.PasoRecorrido;
import org.signbb.backend.navegacion.modelo.PasoRuta;
import org.signbb.backend.navegacion.modelo.Recomendacion;
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
 * por reglas (bucle externo del Algoritmo 3) y cola de opciones pendientes
 * para la selección múltiple.
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
 * terminal, el recorrido corta ahí con esa alerta; si es una pregunta (regla
 * "compuerta"), el recorrido continúa desde ese nodo sin tocar la cola de
 * pendientes. Si ninguna dispara, se sigue por las opciones marcadas.</li>
 * </ol>
 *
 * <p>
 * <b>Selección múltiple y cola de pendientes.</b> Todas las opciones marcadas
 * quedan registradas como respuestas (así las reglas pueden combinarlas). El
 * recorrido sigue a fondo por la mejor de las opciones nuevas y las demás se
 * anotan en la cola de {@link EstadoPaciente}. La cola solo se consulta cuando
 * la rama actual termina en una {@code recomendacion}: esa recomendación se
 * guarda y se continúa con la mejor opción pendiente. El orden (ver
 * {@link #ordenCola}) es: primero las que llevan directo a un terminal grave
 * ({@code urgencia} y luego {@code consulta_medica}), después mayor
 * {@code peso} del nodo destino y, a igual peso, la que llegó antes. Las
 * selecciones múltiples de ramas más profundas entran en la misma cola global.
 * No se encola un destino repetido ni uno que ya se recorrió, y una pregunta
 * ya respondida a la que se vuelve a llegar no se pregunta de nuevo: se siguen
 * las respuestas guardadas.
 *
 * <p>
 * <b>Cómo termina.</b> Un terminal {@code urgencia} o {@code consulta_medica},
 * o una regla con alerta, cierra el recorrido con esa alerta sola (sin las
 * recomendaciones de ramas anteriores) y descarta la cola. Si la cola se
 * agota, el recorrido cierra con nivel {@link NivelAlerta#NORMAL} y trae
 * todas las recomendaciones alcanzadas, cada una con su propio camino.
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
 * {@code signbb.navegacion.max-preguntas}) y el siguiente paso sería preguntar
 * otra, el recorrido cierra en {@link #LIMITE_PREGUNTAS} con nivel
 * {@link NivelAlerta#NORMAL}, junto con las recomendaciones alcanzadas hasta
 * ahí. Las ramas pendientes que no se recorrieron no aportan mensaje. Una
 * regla o un terminal alcanzados en esa misma respuesta tienen prioridad sobre
 * el límite.
 *
 * <p>
 * <b>Camino causal.</b> Se arma con el recorrido real de cada rama (no con una
 * búsqueda en anchura sobre el grafo), de modo que cada recomendación explica
 * por qué preguntas y respuestas se llegó a ella.
 *
 * <p>
 * {@link #retroceder} vuelve a la pregunta anterior para cambiar su
 * respuesta. Cada {@link #paso} apila en {@link EstadoPaciente} un
 * {@link PasoRecorrido} con la pregunta, las opciones, las reglas que esa
 * respuesta marcó y una foto de la cola, la ruta y las recomendaciones tomada
 * antes del paso; retroceder lo desapila y restaura esa foto, de modo que
 * desaparecen exactamente esa respuesta, esas reglas y lo que el paso movió en
 * la cola, sin reevaluar nada. Si el estado no trae pila (una sesión guardada
 * antes de existir), se recurre a reproducir el recorrido desde cero.
 *
 * <p>
 * Como un backend REST es sin estado, el bucle {@code while} de la
 * formulación se descompone en operaciones: {@link #iniciar} (una vez por
 * recorrido), {@link #paso} (una vez por respuesta del cuidador, con una o
 * varias opciones) y {@link #retroceder} (cuando el cuidador vuelve atrás).
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
        estado.reemplazarRuta(List.of(new PasoRuta(v.getId(), "")));
        v = autoAvanzar(grafo, estado, v);
        if (grafo.esNodoTerminal(v)) {
            return seguir(grafo, estado, new Avance(v, estado.ruta()), 0);
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
        List<String> respuestas = Collections.singletonList(respuesta);
        validarRespuestas(grafo, nodoActual, respuestas);
        return registrarYResolver(grafo, base, estado, nodoActual, respuestas);
    }

    /**
     * Selección múltiple: el cuidador marcó varias opciones a la vez para
     * {@code nodoActual}. Se registran TODAS como respuestas del nodo (así las
     * reglas que cruzan varias opciones de la misma pregunta pueden activarse
     * y el recorrido se interrumpe en el terminal más grave). Si ninguna regla
     * dispara, se sigue por la mejor opción (ver {@link #ordenCola}) y las
     * demás quedan en la cola de pendientes para retomarlas cuando la rama
     * termine en una recomendación. Con una sola respuesta marcada el
     * resultado es idéntico al {@code paso} de un solo {@code String}.
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
        return registrarYResolver(grafo, base, estado, nodoActual, respuestasMarcadas);
    }

    /**
     * Vuelve a la última pregunta respondida para que el cuidador cambie su
     * respuesta. Funciona también con el recorrido finalizado (vuelve a la
     * pregunta que lo cerró). Desapila el último {@link PasoRecorrido}: se
     * borra (o restaura) su respuesta y se desmarcan solo las reglas que ese
     * paso marcó, sin reevaluar las reglas.
     *
     * @throws IllegalArgumentException si aún no hay ninguna respuesta.
     * @throws IllegalStateException    si la pregunta ya no existe en el
     *                                  contenido clínico, o si sin pila no se
     *                                  pudo reproducir el recorrido.
     */
    public ResultadoPaso retroceder(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado) {
        if (estado.pasos().isEmpty()) {
            return retrocederReproduciendo(grafo, base, estado);
        }
        PasoRecorrido deshecho = estado.desapilarPaso().orElseThrow();
        Nodo pregunta = grafo.obtenerNodoPorId(deshecho.idPregunta())
                .orElseThrow(() -> new IllegalStateException(
                        "la pregunta '" + deshecho.idPregunta()
                                + "' ya no existe en el contenido clínico"));
        return ResultadoPaso.pregunta(pregunta);
    }

    /**
     * Respaldo de {@link #retroceder} para un estado sin pila de pasos (una
     * sesión guardada antes de que existiera, o respuestas registradas a mano):
     * reinicia la memoria de trabajo y reproduce, en orden, las respuestas
     * anteriores a la última pregunta. Al reproducir con {@link #paso} la pila
     * queda reconstruida.
     */
    private ResultadoPaso retrocederReproduciendo(GrafoClinico grafo, BaseReglas base,
            EstadoPaciente estado) {
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
     * Registra la respuesta ya validada, corre el encadenamiento hacia
     * adelante y apila el {@link PasoRecorrido} con las reglas que este paso
     * marcó (las disparadas después menos las de antes) y la foto de la
     * navegación tomada antes del paso. El máximo de preguntas se aplica al
     * decidir el siguiente paso (ver {@link #seguir}).
     */
    private ResultadoPaso registrarYResolver(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, List<String> respuestas) {
        List<PasoRuta> ruta = rutaHasta(grafo, estado, nodoActual);
        estado.reemplazarRuta(ruta);
        FotoNavegacion foto = estado.fotografiarNavegacion();
        List<String> previas = estado.getRespuestas(nodoActual.getId());
        Set<String> antes = estado.reglasDisparadas();
        int respondidas = estado.pasos().size() + 1;
        estado.registrarRespuestas(nodoActual.getId(), respuestas);
        ResultadoPaso resultado = resolver(grafo, base, estado, nodoActual, respuestas, ruta, respondidas);
        List<String> marcadas = new ArrayList<>(estado.reglasDisparadas());
        marcadas.removeAll(antes);
        estado.apilarPaso(new PasoRecorrido(nodoActual.getId(), respuestas, previas, marcadas, foto));
        return resultado;
    }

    /**
     * Ruta real hasta {@code nodoActual}. Si el estado no la trae (sesión
     * guardada antes de existir la ruta, o nodo al que no se llegó con
     * {@link #iniciar}) se reconstruye con el camino más corto desde el nodo
     * inicial.
     */
    private static List<PasoRuta> rutaHasta(GrafoClinico grafo, EstadoPaciente estado, Nodo nodoActual) {
        List<PasoRuta> ruta = estado.ruta();
        if (!ruta.isEmpty() && ruta.get(ruta.size() - 1).idNodo().equals(nodoActual.getId())) {
            return ruta;
        }
        List<PasoRuta> reconstruida = new ArrayList<>();
        for (PasoCausal p : grafo.obtenerCaminoCausal(nodoActual)) {
            reconstruida.add(new PasoRuta(p.nodo().getId(), p.condicion()));
        }
        if (reconstruida.isEmpty()) {
            reconstruida.add(new PasoRuta(nodoActual.getId(), ""));
        }
        return reconstruida;
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

    /** Siguiente nodo que se visita y el recorrido real que llevó hasta él. */
    private record Avance(Nodo destino, List<PasoRuta> ruta) {
    }

    /** Una opción marcada con su nodo destino, antes de decidir cuál se sigue. */
    private record Candidata(OpcionPendiente opcion, Nodo destino) {
    }

    /**
     * Corre el encadenamiento hacia adelante sobre la memoria de trabajo ya
     * actualizada y decide: corte o compuerta si dispara alguna regla, o
     * avance por las opciones marcadas si no.
     */
    private ResultadoPaso resolver(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
            Nodo nodoActual, List<String> respuestas, List<PasoRuta> ruta, int respondidas) {
        ResultadoInferencia inferencia = servicioReglas.evaluar(base, estado);
        if (!inferencia.esNormal()) {
            return resolverInferencia(grafo, estado, inferencia, ruta, respondidas);
        }
        return seguir(grafo, estado, elegirYEncolar(grafo, estado, nodoActual, respuestas, ruta),
                respondidas);
    }

    /**
     * Resuelve un disparo de reglas. Las reglas ganadoras se marcan como
     * disparadas para que no vuelvan a activarse en el recorrido. Si el
     * destino es un terminal, el recorrido corta ahí con el mayor entre el
     * nivel de la regla y el del desenlace, y la cola se descarta; si es una
     * pregunta, es una regla "compuerta" y el recorrido continúa desde ese nodo
     * (su nivel no se reporta como alerta) dejando la cola intacta.
     */
    private ResultadoPaso resolverInferencia(GrafoClinico grafo, EstadoPaciente estado,
            ResultadoInferencia inferencia, List<PasoRuta> ruta, int respondidas) {
        for (ReglaClinica r : inferencia.reglas()) {
            estado.marcarReglaDisparada(r.getIdRegla());
        }
        Nodo destino = inferencia.destino();
        List<PasoRuta> rutaRegla = new ArrayList<>(ruta);
        rutaRegla.add(new PasoRuta(destino.getId(), ""));
        if (grafo.esNodoTerminal(destino)) {
            return conAlerta(grafo, estado, destino,
                    mayor(inferencia.nivel(), nivelDelDesenlace(destino)), inferencia.reglas(), rutaRegla);
        }
        if (respondidas >= maxPreguntas) {
            return cierreLimite(grafo, estado, rutaRegla);
        }
        estado.reemplazarRuta(rutaRegla);
        return ResultadoPaso.pregunta(destino);
    }

    /**
     * Decide por dónde sigue el recorrido después de responder {@code pregunta}
     * con {@code marcadas}: se sigue la mejor de las opciones (según
     * {@link #ordenCola}) y las demás pasan a la cola de pendientes.
     * <ul>
     * <li>Con una sola opción no hay nada que encolar.</li>
     * <li>Las opciones cuyo destino ya se recorrió (pregunta respondida o
     * recomendación alcanzada) solo se siguen si no hay otra; nunca se
     * encolan.</li>
     * <li>Si el destino elegido ya estaba pendiente, deja de estarlo (se sigue
     * ahora) y conserva su antigüedad para desempatar.</li>
     * <li>Un destino que ya está en la cola no se vuelve a encolar.</li>
     * </ul>
     */
    private Avance elegirYEncolar(GrafoClinico grafo, EstadoPaciente estado, Nodo pregunta,
            List<String> marcadas, List<PasoRuta> ruta) {
        int siguienteOrden = estado.fotografiarNavegacion().siguienteOrden();
        List<OpcionPendiente> pendientes = estado.cola();
        List<Candidata> todas = new ArrayList<>();
        for (int i = 0; i < marcadas.size(); i++) {
            String condicion = marcadas.get(i);
            Nodo destino = grafo.evaluarPaso(pregunta, condicion);
            List<PasoRuta> camino = new ArrayList<>(ruta);
            camino.add(new PasoRuta(destino.getId(), condicion));
            int orden = siguienteOrden + i;
            for (OpcionPendiente p : pendientes) {
                if (p.idDestino().equals(destino.getId())) {
                    orden = p.orden();
                    break;
                }
            }
            todas.add(new Candidata(
                    new OpcionPendiente(pregunta.getId(), condicion, destino.getId(), orden, camino),
                    destino));
        }

        List<Candidata> nuevas = new ArrayList<>();
        for (Candidata c : todas) {
            if (!visitado(grafo, estado, c.destino(), ruta)) {
                nuevas.add(c);
            }
        }
        List<Candidata> aspirantes = nuevas.isEmpty() ? todas : nuevas;
        Candidata elegida = aspirantes.stream()
                .min(Comparator.comparing(Candidata::opcion, ordenCola(grafo)))
                .orElseThrow();

        for (OpcionPendiente p : pendientes) {
            if (p.idDestino().equals(elegida.destino().getId())) {
                estado.quitarDeCola(p);
            }
        }
        for (Candidata c : todas) {
            if (c == elegida || c.destino().equals(elegida.destino())
                    || visitado(grafo, estado, c.destino(), ruta)) {
                continue;
            }
            estado.encolarOpcion(pregunta.getId(), c.opcion().condicion(), c.destino().getId(),
                    c.opcion().camino());
        }
        return new Avance(elegida.destino(), elegida.opcion().camino());
    }

    /**
     * True si el nodo ya se recorrió: una recomendación ya alcanzada, o una
     * pregunta con respuestas guardadas o que está en la ruta actual.
     */
    private static boolean visitado(GrafoClinico grafo, EstadoPaciente estado, Nodo nodo,
            List<PasoRuta> ruta) {
        if (grafo.esNodoTerminal(nodo)) {
            return estado.recomendaciones().containsKey(nodo.getId());
        }
        if (!estado.getRespuestas(nodo.getId()).isEmpty()) {
            return true;
        }
        for (PasoRuta p : ruta) {
            if (p.idNodo().equals(nodo.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sigue el recorrido desde {@code avance} hasta que haya algo que mostrar:
     * <ul>
     * <li>Un terminal {@code urgencia} o {@code consulta_medica}: se cierra con
     * esa alerta sola.</li>
     * <li>Una {@code recomendacion}: se guarda y se continúa con la mejor opción
     * pendiente; si la cola está vacía se cierra en normal con todas las
     * recomendaciones.</li>
     * <li>Una pregunta ya respondida: no se vuelve a preguntar, se siguen sus
     * respuestas guardadas.</li>
     * <li>Una pregunta sin responder: se pregunta, salvo que ya se hayan
     * respondido {@code maxPreguntas}, caso en que se cierra en
     * {@link #LIMITE_PREGUNTAS}.</li>
     * </ul>
     *
     * @param respondidas preguntas respondidas contando la que se está
     *                    procesando.
     */
    private ResultadoPaso seguir(GrafoClinico grafo, EstadoPaciente estado, Avance avance,
            int respondidas) {
        Nodo actual = avance.destino();
        List<PasoRuta> ruta = avance.ruta();
        Set<String> vistasEnRama = new HashSet<>();
        while (true) {
            if (grafo.esNodoTerminal(actual)) {
                NivelAlerta nivel = nivelDelDesenlace(actual);
                if (nivel != NivelAlerta.NORMAL) {
                    return conAlerta(grafo, estado, actual, nivel, List.of(), ruta);
                }
                estado.registrarRecomendacion(actual.getId(), ruta);
                Optional<OpcionPendiente> siguiente = mejorOpcion(estado.cola(), grafo);
                if (siguiente.isEmpty()) {
                    return finalNormal(grafo, estado, actual, ruta);
                }
                estado.quitarDeCola(siguiente.get());
                actual = destinoDe(grafo, siguiente.get());
                ruta = siguiente.get().camino();
                vistasEnRama.clear();
                continue;
            }
            List<String> guardadas = estado.getRespuestas(actual.getId());
            if (!guardadas.isEmpty()) {
                if (!vistasEnRama.add(actual.getId())) {
                    throw new IllegalStateException("el recorrido volvió a la pregunta '"
                            + actual.getId() + "' dentro de la misma rama: el grafo tiene un ciclo");
                }
                Avance siguiente = elegirYEncolar(grafo, estado, actual, guardadas, ruta);
                actual = siguiente.destino();
                ruta = siguiente.ruta();
                continue;
            }
            if (respondidas >= maxPreguntas) {
                return cierreLimite(grafo, estado, ruta);
            }
            estado.reemplazarRuta(ruta);
            return ResultadoPaso.pregunta(actual);
        }
    }

    /**
     * Cierra el recorrido con una alerta ({@code urgencia}, {@code consulta_medica}
     * o regla): solo se muestra la alerta, sin las recomendaciones de ramas
     * anteriores, y la cola de pendientes se descarta.
     */
    private ResultadoPaso conAlerta(GrafoClinico grafo, EstadoPaciente estado, Nodo destino,
            NivelAlerta nivel, List<ReglaClinica> razones, List<PasoRuta> ruta) {
        estado.vaciarCola();
        return ResultadoPaso.terminal(destino, nivel, razones, caminoDe(grafo, ruta));
    }

    /**
     * Cierra el recorrido en normal porque ya no quedan opciones pendientes:
     * muestra todas las recomendaciones alcanzadas, cada una con su camino. El
     * destino y el camino principal son los de la última.
     */
    private ResultadoPaso finalNormal(GrafoClinico grafo, EstadoPaciente estado, Nodo ultima,
            List<PasoRuta> ruta) {
        estado.vaciarCola();
        return ResultadoPaso.terminal(ultima, NivelAlerta.NORMAL, List.of(), caminoDe(grafo, ruta),
                recomendacionesDe(grafo, estado));
    }

    /**
     * Cierra el recorrido en {@link #LIMITE_PREGUNTAS}: mensaje general de que
     * no se encontraron señales de alarma más las recomendaciones alcanzadas
     * hasta ahora. Las ramas pendientes no aportan mensaje. El camino llega
     * hasta la pregunta que quedó sin responder.
     */
    private ResultadoPaso cierreLimite(GrafoClinico grafo, EstadoPaciente estado, List<PasoRuta> ruta) {
        estado.vaciarCola();
        return ResultadoPaso.terminal(LIMITE_PREGUNTAS, NivelAlerta.NORMAL, List.of(),
                caminoDe(grafo, ruta), recomendacionesDe(grafo, estado));
    }

    /** Recomendaciones alcanzadas hasta ahora, en orden de llegada, con su camino. */
    private static List<Recomendacion> recomendacionesDe(GrafoClinico grafo, EstadoPaciente estado) {
        List<Recomendacion> recomendaciones = new ArrayList<>();
        for (Map.Entry<String, List<PasoRuta>> e : estado.recomendaciones().entrySet()) {
            Nodo nodo = grafo.obtenerNodoPorId(e.getKey())
                    .orElseThrow(() -> new IllegalStateException("la recomendación '" + e.getKey()
                            + "' ya no existe en el grafo"));
            recomendaciones.add(new Recomendacion(nodo, caminoDe(grafo, e.getValue())));
        }
        return recomendaciones;
    }

    /** Convierte una ruta guardada por ids en el camino causal con los nodos del grafo. */
    private static List<PasoCausal> caminoDe(GrafoClinico grafo, List<PasoRuta> ruta) {
        List<PasoCausal> camino = new ArrayList<>();
        for (PasoRuta p : ruta) {
            Nodo nodo = grafo.obtenerNodoPorId(p.idNodo())
                    .orElseThrow(() -> new IllegalStateException("el nodo '" + p.idNodo()
                            + "' de la ruta ya no existe en el grafo"));
            camino.add(new PasoCausal(nodo, p.condicion()));
        }
        return camino;
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
     * Orden de la cola de opciones pendientes: la primera es la que se debe
     * seguir antes. Tres criterios, en este orden:
     * <ol>
     * <li>Las que llevan directo a un terminal grave, porque terminan la
     * navegación: primero {@code urgencia}, luego {@code consulta_medica}.</li>
     * <li>Mayor {@code peso} del nodo destino.</li>
     * <li>Menor {@code orden} de llegada (la más antigua).</li>
     * </ol>
     *
     * @throws IllegalStateException al comparar si un destino ya no existe en
     *                               el grafo.
     */
    public static Comparator<OpcionPendiente> ordenCola(GrafoClinico grafo) {
        return (a, b) -> {
            Nodo na = destinoDe(grafo, a);
            Nodo nb = destinoDe(grafo, b);
            int cmp = Integer.compare(severidadTerminal(nb), severidadTerminal(na));
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(nb.getPeso(), na.getPeso());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a.orden(), b.orden());
        };
    }

    /**
     * Severidad de un nodo como destino de una opción: 2 si es un terminal de
     * {@code urgencia}, 1 si lo es de {@code consulta_medica} y 0 en cualquier
     * otro caso (preguntas y recomendaciones).
     */
    public static int severidadTerminal(Nodo nodo) {
        return switch (nodo.getTipo()) {
            case "urgencia" -> 2;
            case "consulta_medica" -> 1;
            default -> 0;
        };
    }

    /** La opción que se debe seguir primero según {@link #ordenCola}, si hay alguna. */
    public static Optional<OpcionPendiente> mejorOpcion(List<OpcionPendiente> opciones,
            GrafoClinico grafo) {
        return opciones.stream().min(ordenCola(grafo));
    }

    /** Nodo destino de una opción pendiente; falla si ya no existe en el grafo. */
    private static Nodo destinoDe(GrafoClinico grafo, OpcionPendiente opcion) {
        return grafo.obtenerNodoPorId(opcion.idDestino())
                .orElseThrow(() -> new IllegalStateException(
                        "la opción pendiente apunta al nodo '" + opcion.idDestino()
                                + "' que ya no existe en el grafo"));
    }

    /**
     * Avanza automáticamente mientras el nodo tenga una única arista de arranque
     * {@code inicia_navegacion} (el nodo {@code inicio} del grafo). No registra
     * respuestas porque no corresponde a una pregunta real al cuidador, pero sí
     * agrega cada paso a la ruta.
     */
    private Nodo autoAvanzar(GrafoClinico grafo, EstadoPaciente estado, Nodo v) {
        while (!grafo.esNodoTerminal(v)) {
            List<Respuesta> opciones = grafo.obtenerOpciones(v);
            if (opciones.size() == 1
                    && INICIA_NAVEGACION.equals(opciones.get(0).getCondicion())) {
                v = grafo.evaluarPaso(v, INICIA_NAVEGACION);
                estado.agregarARuta(new PasoRuta(v.getId(), INICIA_NAVEGACION));
            } else {
                break;
            }
        }
        return v;
    }
}
