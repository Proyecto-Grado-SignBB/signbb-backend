package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.OpcionPendiente;
import org.signbb.backend.navegacion.modelo.PasoCausal;
import org.signbb.backend.navegacion.modelo.Recomendacion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Escenarios de la cola de opciones pendientes de la selección múltiple, con
 * grafos armados en código (sin JSON). Cubren: seguir la rama más grave a
 * fondo y retomar la cola al terminar en una recomendación, alerta que anula
 * lo anterior, selecciones múltiples más profundas sobre la misma cola,
 * duplicados y revisitas, umbral de preguntas, reglas (compuerta y alerta) y
 * {@code retroceder}.
 */
class MotorNavegacionColaTest {

    private GrafoClinico grafo;
    private BaseReglas base;
    private EstadoPaciente estado;
    private MotorNavegacion motor;
    private final Map<String, Nodo> nodos = new HashMap<>();

    @BeforeEach
    void preparar() {
        grafo = new GrafoClinico();
        base = new BaseReglas();
        estado = new EstadoPaciente();
        motor = new MotorNavegacion(new ServicioReglasImpl());
        nodos.clear();
        pregunta("inicio", 0);
    }

    // ---------- armado del grafo ----------

    private Nodo agregar(Nodo n) {
        grafo.agregarNodo(n);
        nodos.put(n.getId(), n);
        return n;
    }

    private Nodo pregunta(String id, int peso) {
        return agregar(new Nodo(id, "pregunta", id, peso));
    }

    private Nodo multiple(String id, int peso) {
        return agregar(new Nodo(id, "pregunta", id, peso, false, List.of(), "SELECCION_MULTIPLE"));
    }

    private Nodo recomendacion(String id) {
        return agregar(new Nodo(id, "recomendacion", id, 1));
    }

    private Nodo urgencia(String id) {
        return agregar(new Nodo(id, "urgencia", id, 1));
    }

    private Nodo consulta(String id) {
        return agregar(new Nodo(id, "consulta_medica", id, 1));
    }

    private void arista(String origen, String condicion, String destino) {
        grafo.conectarRespuestas(nodos.get(origen), nodos.get(destino), condicion);
    }

    /** Conecta {@code inicio} con la primera pregunta real. */
    private ResultadoPaso empezarEn(String primera) {
        arista("inicio", "inicia_navegacion", primera);
        return motor.iniciar(estado, grafo);
    }

    private ResultadoPaso responder(ResultadoPaso actual, String... marcadas) {
        assertFalse(actual.finalizado(), "se esperaba una pregunta pendiente");
        return motor.paso(grafo, base, estado, actual.preguntaActual(), List.of(marcadas));
    }

    private void regla(String id, String nodo, String respuesta, String destino, NivelAlerta nivel) {
        base.agregarRegla(new ReglaClinica(id, id, List.of(new Condicion(nodo, respuesta)),
                nodos.get(destino), nivel));
    }

    // ---------- lectura de resultados ----------

    private static List<String> ids(List<PasoCausal> camino) {
        return camino.stream().map(p -> p.nodo().getId()).toList();
    }

    private static List<String> condiciones(List<PasoCausal> camino) {
        return camino.stream().map(PasoCausal::condicion).toList();
    }

    private static List<String> idsRecomendaciones(ResultadoPaso r) {
        return r.recomendaciones().stream().map(x -> x.nodo().getId()).toList();
    }

    private List<String> idsCola() {
        return estado.cola().stream().map(OpcionPendiente::idDestino).toList();
    }

    private static Recomendacion recomendacion(ResultadoPaso r, String id) {
        return r.recomendaciones().stream().filter(x -> x.nodo().getId().equals(id)).findFirst().orElseThrow();
    }

    // ---------- escenarios ----------

    /** q1 (múltiple): A→qa (peso 5), B→qb (peso 3); cada una termina en su recomendación. */
    private void grafoDosRamas() {
        multiple("q1", 1);
        pregunta("qa", 5);
        pregunta("qb", 3);
        recomendacion("ra");
        recomendacion("rb");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "x", "ra");
        arista("qb", "y", "rb");
    }

    @Test
    void sigueLaRamaMasGraveAFondoYLuegoRetomaLaCola() {
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "B", "A");
        assertEquals("qa", r.preguntaActual().getId(), "primero la de mayor peso");
        assertEquals(List.of("qb"), idsCola());

        r = responder(r, "x");
        assertFalse(r.finalizado(), "la recomendación no cierra: queda una opción pendiente");
        assertEquals("qb", r.preguntaActual().getId());
        assertTrue(idsCola().isEmpty());
        assertEquals(List.of("ra"), List.copyOf(estado.recomendaciones().keySet()));

        r = responder(r, "y");
        assertTrue(r.finalizado());
        assertEquals(NivelAlerta.NORMAL, r.nivel());
        assertEquals("rb", r.destino().getId());
        assertEquals(List.of("ra", "rb"), idsRecomendaciones(r));
    }

    @Test
    void cadaRecomendacionTieneSuPropioCamino() {
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");
        r = responder(r, "A", "B");
        r = responder(r, "x");
        r = responder(r, "y");

        assertEquals(List.of("inicio", "q1", "qa", "ra"), ids(recomendacion(r, "ra").camino()));
        assertEquals(List.of("", "inicia_navegacion", "A", "x"),
                condiciones(recomendacion(r, "ra").camino()));
        assertEquals(List.of("inicio", "q1", "qb", "rb"), ids(recomendacion(r, "rb").camino()));
        assertEquals(List.of("", "inicia_navegacion", "B", "y"),
                condiciones(recomendacion(r, "rb").camino()));
        assertEquals(ids(recomendacion(r, "rb").camino()), ids(r.caminoCausal()),
                "el camino principal es el de la última recomendación");
    }

    @Test
    void todasLasOpcionesMarcadasQuedanRegistradasComoRespuestas() {
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");
        responder(r, "A", "B");
        assertEquals(List.of("A", "B"), estado.getRespuestas("q1"));
    }

    @Test
    void unaOpcionQueLlevaDirectoAUrgenciaCierraSinPreguntarLoDemas() {
        multiple("q1", 1);
        pregunta("qa", 99);
        urgencia("u");
        arista("q1", "A", "qa");
        arista("q1", "B", "u");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        assertTrue(r.finalizado());
        assertEquals(NivelAlerta.URGENCIA, r.nivel());
        assertEquals("u", r.destino().getId());
        assertTrue(r.recomendaciones().isEmpty());
        assertTrue(idsCola().isEmpty());
        assertEquals(List.of("inicio", "q1", "u"), ids(r.caminoCausal()));
    }

    @Test
    void consultaMedicaDirectaTambienPasaPorEncimaDelPeso() {
        multiple("q1", 1);
        pregunta("qa", 99);
        consulta("c");
        arista("q1", "A", "qa");
        arista("q1", "B", "c");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        assertTrue(r.finalizado());
        assertEquals(NivelAlerta.CONSULTAR_MEDICO, r.nivel());
        assertEquals("c", r.destino().getId());
    }

    @Test
    void recomendacionYLuegoAlertaDejaSoloLaAlerta() {
        multiple("q1", 1);
        pregunta("qa", 5);
        pregunta("qb", 3);
        recomendacion("ra");
        urgencia("u");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "x", "ra");
        arista("qb", "y", "u");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        r = responder(r, "x");
        assertEquals("qb", r.preguntaActual().getId());
        r = responder(r, "y");

        assertTrue(r.finalizado());
        assertEquals(NivelAlerta.URGENCIA, r.nivel());
        assertEquals("u", r.destino().getId());
        assertTrue(r.recomendaciones().isEmpty(), "la recomendación anterior no se muestra con la alerta");
        assertTrue(idsCola().isEmpty());
        assertEquals(List.of("inicio", "q1", "qb", "u"), ids(r.caminoCausal()));
    }

    /**
     * q1 (múltiple): A→qa (5), B→qb (3). qa (múltiple): X→qx (4), Y→qy (pesoQy).
     * Cada pregunta termina en su recomendación.
     */
    private void grafoProfundo(int pesoQy) {
        multiple("q1", 1);
        multiple("qa", 5);
        pregunta("qb", 3);
        pregunta("qx", 4);
        pregunta("qy", pesoQy);
        recomendacion("rb");
        recomendacion("rx");
        recomendacion("ry");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "X", "qx");
        arista("qa", "Y", "qy");
        arista("qb", "k", "rb");
        arista("qx", "k", "rx");
        arista("qy", "k", "ry");
    }

    // ---------- umbral ----------

    private MotorNavegacion motorConMaximo(int max) {
        return new MotorNavegacion(new ServicioReglasImpl(), max);
    }

    // ---------- reglas ----------

    @Test
    void unaReglaConAlertaTerminaConLaAlertaYDescartaLaCola() {
        grafoDosRamas();
        urgencia("u");
        regla("grave", "qa", "x", "u", NivelAlerta.URGENCIA);
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        r = responder(r, "x");

        assertTrue(r.finalizado());
        assertEquals(NivelAlerta.URGENCIA, r.nivel());
        assertEquals("u", r.destino().getId());
        assertEquals(1, r.razones().size());
        assertTrue(r.recomendaciones().isEmpty());
        assertTrue(idsCola().isEmpty());
        assertEquals(List.of("inicio", "q1", "qa", "u"), ids(r.caminoCausal()));
    }

    @Test
    void unaReglaQueCruzaVariasOpcionesDeLaMismaPreguntaDisparaConTodasMarcadas() {
        grafoDosRamas();
        urgencia("u");
        base.agregarRegla(new ReglaClinica("combo", "combo",
                List.of(new Condicion("q1", "A"), new Condicion("q1", "B")), nodos.get("u"),
                NivelAlerta.URGENCIA));
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        assertTrue(r.finalizado());
        assertEquals("u", r.destino().getId());
        assertTrue(idsCola().isEmpty());
    }

    // ---------- retroceder ----------

    @Test
    void retrocederDesdeUnResultadoFinalVuelveALaUltimaPregunta() {
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");
        r = responder(r, "A", "B");
        r = responder(r, "x");
        r = responder(r, "y");
        assertTrue(r.finalizado());

        r = motor.retroceder(grafo, base, estado);
        assertEquals("qb", r.preguntaActual().getId());
        assertEquals(List.of("ra"), List.copyOf(estado.recomendaciones().keySet()));
        assertTrue(idsCola().isEmpty());

        r = responder(r, "y");
        assertEquals(List.of("ra", "rb"), idsRecomendaciones(r));
    }

    @Test
    void retrocederHastaLaSeleccionMultiplePermiteCambiarLasOpciones() {
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");
        r = responder(r, "A", "B");
        r = responder(r, "x");

        r = motor.retroceder(grafo, base, estado);
        r = motor.retroceder(grafo, base, estado);

        assertEquals("q1", r.preguntaActual().getId());
        assertTrue(idsCola().isEmpty());
        assertTrue(estado.recomendaciones().isEmpty());
        assertTrue(estado.getRespuestas("q1").isEmpty());

        r = responder(r, "B");
        assertEquals("qb", r.preguntaActual().getId());
        assertTrue(idsCola().isEmpty());
    }
}
