package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.CONSULTA;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.DECAIDO;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.INICIO;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.MAS_39;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.MENOS_38;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.RECOMENDACION;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.URGENCIA;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.servicios.EditorLienzo.DatosNodo;
import org.signbb.backend.autoria.servicios.EditorLienzo.DatosRespuesta;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.GrafoClinico;

/** Tests de {@link CompiladorSubgrafo}: del lienzo al formato de los subgrafos del sistema. */
class CompiladorSubgrafoTest {

    private static final Instant FECHA = Instant.parse("2026-10-09T15:00:00Z");

    private static GrafoClinicoJson.Grafo compilar(DocumentoLienzo d) {
        return CompiladorSubgrafo.compilar(LienzoFiebre.borrador(d), FECHA).graph();
    }

    private static GrafoClinicoJson.MetadataNodo meta(GrafoClinicoJson.Grafo g, String id) {
        return g.nodes().get(id).metadata();
    }

    @Test
    void losDatosDelBorradorVanEnElGrafo() {
        GrafoClinicoJson.Grafo g = compilar(LienzoFiebre.completo());

        assertEquals("grafo_fiebre", g.id());
        assertEquals("Fiebre", g.label());
        assertEquals(List.of("Actividad y color"), g.category());
        assertEquals(INICIO, g.preguntaInicial());
        assertEquals("Dra. Prueba", g.metadata().get("autor"));
        assertEquals("Hospital de prueba", g.metadata().get("institucion"));
        assertEquals("Fiebre o temperatura alta", g.metadata().get("etiquetaOpcion"));
        assertEquals("2026-10-09T15:00:00Z", g.metadata().get("fechaPublicacion"));
    }

    @Test
    void elPesoEsLaMayorGravedadDeLasRespuestasQueLlegan() {
        GrafoClinicoJson.Grafo g = compilar(LienzoFiebre.completo());

        assertEquals(1, meta(g, INICIO).peso(), "a la primera pregunta no llega nadie");
        assertEquals(3, meta(g, URGENCIA).peso());
        // "Menos de 38" (LEVE) y "Entre 38 y 39" (MODERADA) llegan a la misma pregunta: gana la mayor.
        assertEquals(2, meta(g, DECAIDO).peso());
        assertEquals(2, meta(g, CONSULTA).peso());
        assertEquals(1, meta(g, RECOMENDACION).peso());
    }

    @Test
    void lasPreguntasLlevanSuTipoDeInteraccion() {
        DocumentoLienzo varias = EditorLienzo.editarNodo(LienzoFiebre.completo(), DECAIDO,
                new DatosNodo(null, "¿Qué nota?", true, null, List.of(
                        new DatosRespuesta(LienzoFiebre.SI, "Decaído", Gravedad.MODERADA),
                        new DatosRespuesta(LienzoFiebre.NO, "Nada", Gravedad.LEVE))));

        GrafoClinicoJson.Grafo g = compilar(varias);

        assertEquals("pregunta", meta(g, INICIO).tipo());
        assertEquals("SELECCION_UNICA", meta(g, INICIO).tipoInteraccion());
        assertEquals("SELECCION_MULTIPLE", meta(g, DECAIDO).tipoInteraccion());
        assertFalse(meta(g, INICIO).archivos());
        assertEquals(List.of(), meta(g, INICIO).src());
    }

    @Test
    void lasConclusionesSonTerminalesConSuDesenlaceYMensaje() {
        GrafoClinicoJson.Grafo g = compilar(LienzoFiebre.completo());

        assertEquals("terminal", meta(g, URGENCIA).tipo());
        assertEquals("urgencia", meta(g, URGENCIA).desenlace());
        assertEquals("consulta_medica", meta(g, CONSULTA).desenlace());
        assertEquals("recomendacion", meta(g, RECOMENDACION).desenlace());
        assertEquals("Lleve al bebé a urgencias ahora.", g.nodes().get(URGENCIA).label());
        assertNull(meta(g, URGENCIA).tipoInteraccion());
    }

    @Test
    void cadaRespuestaEsUnaArista() {
        GrafoClinicoJson.Grafo g = compilar(LienzoFiebre.completo());

        assertEquals(5, g.edges().size(), "3 respuestas de la primera pregunta y 2 de la segunda");
        assertTrue(g.edges().contains(new GrafoClinicoJson.Arista(INICIO, "Más de 39 °C", URGENCIA, true)));
        assertTrue(g.edges().contains(new GrafoClinicoJson.Arista(INICIO, "Menos de 38 °C", DECAIDO, true)));
    }

    @Test
    void dosRespuestasPuedenLlegarALaMismaConclusion() {
        DocumentoLienzo compartido = EditorLienzo.enlazar(LienzoFiebre.completo(), MENOS_38, CONSULTA);
        GrafoClinicoJson.Grafo g = compilar(compartido);

        assertEquals(5, g.nodes().size(), "la conclusión compartida se escribe una sola vez");
        assertTrue(g.edges().contains(new GrafoClinicoJson.Arista(INICIO, "Menos de 38 °C", CONSULTA, true)));
        assertTrue(g.edges().contains(new GrafoClinicoJson.Arista(DECAIDO, "Sí", CONSULTA, true)));
        assertEquals(2, meta(g, CONSULTA).peso(), "pesa la mayor gravedad de las respuestas que llegan");
        assertTrue(GrafoClinicoMapper.desde(CompiladorSubgrafo.compilar(
                LienzoFiebre.borrador(compartido), FECHA)).validarCiclo());
    }

    @Test
    void laSalidaEsUnGrafoClinicoValido() {
        GrafoClinico tad = GrafoClinicoMapper.desde(CompiladorSubgrafo.compilar(
                LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA));

        assertEquals(5, tad.size());
        assertTrue(tad.validarCiclo());
        NodoLienzo inicio = LienzoFiebre.completo().nodos().get(INICIO);
        assertEquals(inicio.texto(), tad.obtenerNodoInicial().orElseThrow().getContenido());
    }
}
