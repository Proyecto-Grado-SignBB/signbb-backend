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
        estado = new EstadoPaciente("sesion-prueba");
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
    void aIgualPesoSigueLaPrimeraMarcada() {
        multiple("q1", 1);
        pregunta("qa", 3);
        pregunta("qb", 3);
        recomendacion("ra");
        recomendacion("rb");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "x", "ra");
        arista("qb", "y", "rb");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "B", "A");
        assertEquals("qb", r.preguntaActual().getId());
        assertEquals(List.of("qa"), idsCola());
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

    @Test
    void laSeleccionMultiplePosteriorEntraEnLaMismaColaPorGravedad() {
        grafoProfundo(2);
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        assertEquals("qa", r.preguntaActual().getId());
        r = responder(r, "X", "Y");
        assertEquals("qx", r.preguntaActual().getId());
        assertEquals(List.of("qb", "qy"), idsCola());

        r = responder(r, "k");
        assertEquals("qb", r.preguntaActual().getId(), "qb (3) pesa más que qy (2)");
        r = responder(r, "k");
        assertEquals("qy", r.preguntaActual().getId());
        r = responder(r, "k");

        assertTrue(r.finalizado());
        assertEquals(List.of("rx", "rb", "ry"), idsRecomendaciones(r));
    }

    @Test
    void aIgualGravedadEnLaColaGanaLaMasAntigua() {
        grafoProfundo(3);
        ResultadoPaso r = empezarEn("q1");
        r = responder(r, "A", "B");
        r = responder(r, "X", "Y");
        r = responder(r, "k");

        assertEquals("qb", r.preguntaActual().getId(), "qb entró antes que qy");
        r = responder(r, "k");
        assertEquals("qy", r.preguntaActual().getId());
    }

    @Test
    void unDestinoYaPendienteNoSeEncolaDosVeces() {
        multiple("q1", 1);
        multiple("qa", 5);
        pregunta("qb", 3);
        pregunta("qc", 2);
        recomendacion("rb");
        recomendacion("rc");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "X", "qb");
        arista("qa", "Y", "qc");
        arista("qb", "k", "rb");
        arista("qc", "k", "rc");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        assertEquals(List.of("qb"), idsCola());
        r = responder(r, "X", "Y");

        assertEquals("qb", r.preguntaActual().getId(), "qb ya estaba pendiente y es la más grave");
        assertEquals(List.of("qc"), idsCola(), "qb sale de la cola al seguirse y qc entra una sola vez");
        r = responder(r, "k");
        assertEquals("qc", r.preguntaActual().getId());
        r = responder(r, "k");
        assertEquals(List.of("rb", "rc"), idsRecomendaciones(r));
    }

    @Test
    void unaPreguntaYaRespondidaNoSeVuelveAPreguntar() {
        multiple("q1", 1);
        pregunta("qa", 5);
        pregunta("qb", 3);
        pregunta("e", 2);
        recomendacion("re");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "x", "e");
        arista("qb", "y", "e");
        arista("e", "z", "re");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        r = responder(r, "x");
        assertEquals("e", r.preguntaActual().getId());
        r = responder(r, "z");
        assertEquals("qb", r.preguntaActual().getId());
        r = responder(r, "y");

        assertTrue(r.finalizado(), "e ya estaba respondida: se siguen sus respuestas guardadas");
        assertEquals(List.of("re"), idsRecomendaciones(r));
        assertEquals(NivelAlerta.NORMAL, r.nivel());
    }

    @Test
    void unaRecomendacionRepetidaPorOtraRamaNoSeDuplica() {
        multiple("q1", 1);
        pregunta("qa", 5);
        pregunta("qb", 3);
        recomendacion("comun");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "x", "comun");
        arista("qb", "y", "comun");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        r = responder(r, "x");
        r = responder(r, "y");

        assertTrue(r.finalizado());
        assertEquals(List.of("comun"), idsRecomendaciones(r));
    }

    @Test
    void conUnaSolaRespuestaNoHayColaYSeSigueElMismoCamino() {
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "B");
        assertEquals("qb", r.preguntaActual().getId());
        assertTrue(idsCola().isEmpty());
        r = responder(r, "y");

        assertTrue(r.finalizado());
        assertEquals("rb", r.destino().getId());
        assertEquals(List.of("rb"), idsRecomendaciones(r));
        assertEquals(List.of("inicio", "q1", "qb", "rb"), ids(r.caminoCausal()));
    }

    // ---------- umbral ----------

    private MotorNavegacion motorConMaximo(int max) {
        return new MotorNavegacion(new ServicioReglasImpl(), max);
    }

    @Test
    void elUmbralCortaCuandoElSiguientePasoEsOtraPreguntaYTraeLasRecomendacionesLogradas() {
        motor = motorConMaximo(2);
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        assertEquals("qa", r.preguntaActual().getId());
        r = responder(r, "x");

        assertTrue(r.finalizado());
        assertEquals(NivelAlerta.NORMAL, r.nivel());
        assertEquals(MotorNavegacion.LIMITE_PREGUNTAS.getId(), r.destino().getId());
        assertEquals(List.of("ra"), idsRecomendaciones(r), "la rama pendiente no aporta mensaje");
        assertEquals(List.of("inicio", "q1", "qb"), ids(r.caminoCausal()),
                "el camino llega a la pregunta que quedó sin responder");
        assertTrue(idsCola().isEmpty());
    }

    @Test
    void elUmbralNoCortaSiLoPendienteEsUnaRecomendacion() {
        motor = motorConMaximo(2);
        multiple("q1", 1);
        pregunta("qa", 5);
        recomendacion("ra");
        recomendacion("rc");
        arista("q1", "A", "qa");
        arista("q1", "C", "rc");
        arista("qa", "x", "ra");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "C");
        r = responder(r, "x");

        assertTrue(r.finalizado());
        assertEquals("rc", r.destino().getId());
        assertEquals(List.of("ra", "rc"), idsRecomendaciones(r));
    }

    @Test
    void elUmbralNoCortaSiHayAlertaEnEsaMismaRespuesta() {
        motor = motorConMaximo(2);
        multiple("q1", 1);
        pregunta("qa", 5);
        pregunta("qb", 3);
        urgencia("u");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "x", "u");
        arista("qb", "y", "u");
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        r = responder(r, "x");
        assertEquals(NivelAlerta.URGENCIA, r.nivel());
        assertEquals("u", r.destino().getId());
    }

    // ---------- reglas ----------

    @Test
    void unaReglaCompuertaNoTocaLaCola() {
        grafoDosRamas();
        pregunta("qg", 1);
        recomendacion("rg");
        arista("qg", "k", "rg");
        regla("gate", "qa", "x", "qg", NivelAlerta.CONSULTAR_MEDICO);
        ResultadoPaso r = empezarEn("q1");

        r = responder(r, "A", "B");
        r = responder(r, "x");
        assertEquals("qg", r.preguntaActual().getId(), "la regla salta a la pregunta compuerta");
        assertEquals(List.of("qb"), idsCola(), "la cola queda intacta");

        r = responder(r, "k");
        assertEquals("qb", r.preguntaActual().getId());
        r = responder(r, "y");
        assertEquals(List.of("rg", "rb"), idsRecomendaciones(r));
    }

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
    void retrocederDespuesDeSaltarALaColaRestauraColaYRecomendaciones() {
        grafoDosRamas();
        arista("qa", "w", "ra");
        ResultadoPaso r = empezarEn("q1");
        r = responder(r, "A", "B");
        r = responder(r, "x");
        assertEquals("qb", r.preguntaActual().getId());

        r = motor.retroceder(grafo, base, estado);

        assertEquals("qa", r.preguntaActual().getId());
        assertEquals(List.of("qb"), idsCola(), "qb vuelve a estar pendiente");
        assertTrue(estado.recomendaciones().isEmpty());
        assertTrue(estado.getRespuestas("qa").isEmpty());

        r = responder(r, "w");
        assertEquals("qb", r.preguntaActual().getId());
    }

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

    @Test
    void retrocederTrasElCierrePorUmbralRestauraLaCola() {
        motor = motorConMaximo(2);
        grafoDosRamas();
        ResultadoPaso r = empezarEn("q1");
        r = responder(r, "A", "B");
        r = responder(r, "x");
        assertEquals(MotorNavegacion.LIMITE_PREGUNTAS.getId(), r.destino().getId());

        r = motor.retroceder(grafo, base, estado);
        assertEquals("qa", r.preguntaActual().getId());
        assertEquals(List.of("qb"), idsCola());
    }
}
