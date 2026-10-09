package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.CONSULTA;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.DECAIDO;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.ENTRE_38_39;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.INICIO;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.MAS_39;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.MENOS_38;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.NO;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.RECOMENDACION;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.SI;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.URGENCIA;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.conclusion;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.pregunta;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.autoria.servicios.EditorLienzo.DatosNodo;
import org.signbb.backend.autoria.servicios.EditorLienzo.DatosRespuesta;
import org.signbb.backend.config.RecursoNoEncontradoException;

/**
 * Tests de {@link EditorLienzo}: el "+", enlazar a una pregunta existente,
 * editar y eliminar, y lo que se rechaza en el momento de editar.
 */
class EditorLienzoTest {

    private static RespuestaLienzo respuesta(DocumentoLienzo d, String nodoId, String respuestaId) {
        return d.nodos().get(nodoId).respuestas().stream()
                .filter(r -> r.id().equals(respuestaId)).findFirst().orElseThrow();
    }

    @Test
    void laPrimeraPreguntaEsElInicioYSusRespuestasQuedanComoMas() {
        DocumentoLienzo d = LienzoFiebre.primeraPregunta();

        assertEquals(INICIO, d.inicio());
        NodoLienzo inicio = d.nodos().get(INICIO);
        assertEquals(TipoNodo.PREGUNTA, inicio.tipo());
        assertEquals(List.of(MENOS_38, ENTRE_38_39, MAS_39),
                inicio.respuestas().stream().map(RespuestaLienzo::id).toList());
        assertTrue(inicio.respuestas().stream().allMatch(r -> r.destino() == null));
        assertEquals(Gravedad.GRAVE, respuesta(d, INICIO, MAS_39).gravedad());
    }

    @Test
    void elMasCreaElNodoYConectaLaRespuesta() {
        DocumentoLienzo d = EditorLienzo.agregarNodo(LienzoFiebre.primeraPregunta(), INICIO, MAS_39,
                conclusion(Desenlace.URGENCIA, "Vaya a urgencias"));

        assertEquals(URGENCIA, respuesta(d, INICIO, MAS_39).destino());
        NodoLienzo urgencia = d.nodos().get(URGENCIA);
        assertEquals(TipoNodo.CONCLUSION, urgencia.tipo());
        assertEquals(Desenlace.URGENCIA, urgencia.desenlace());
        assertTrue(urgencia.respuestas().isEmpty());
    }

    @Test
    void enlazarAUnaPreguntaExistenteNoCreaNodos() {
        DocumentoLienzo d = LienzoFiebre.completo();

        assertEquals(DECAIDO, respuesta(d, INICIO, MENOS_38).destino());
        assertEquals(DECAIDO, respuesta(d, INICIO, ENTRE_38_39).destino());
        assertEquals(5, d.nodos().size(), "2 preguntas y 3 conclusiones");
    }

    @Test
    void enlazarANullVuelveLaRespuestaUnMasYDejaLaRamaSuelta() {
        DocumentoLienzo d = EditorLienzo.enlazar(LienzoFiebre.completo(), MAS_39, null);

        assertNull(respuesta(d, INICIO, MAS_39).destino());
        assertTrue(d.nodos().containsKey(URGENCIA), "no se borra lo que colgaba de la respuesta");
    }

    @Test
    void segundaPrimeraPreguntaSeRechaza() {
        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.agregarNodo(
                LienzoFiebre.primeraPregunta(), null, null, pregunta("¿Otra?", false, "a", null, "b", null)));
    }

    @Test
    void elSubgrafoNoPuedeEmpezarConUnaConclusion() {
        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.agregarNodo(
                DocumentoLienzo.vacio(), null, null, conclusion(Desenlace.RECOMENDACION, "Todo bien")));
    }

    @Test
    void unaRespuestaQueYaTieneCaminoNoAceptaOtroNodo() {
        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.agregarNodo(
                LienzoFiebre.completo(), INICIO, MAS_39, conclusion(Desenlace.URGENCIA, "Otra")));
    }

    @Test
    void respuestasRepetidasSeRechazanSinImportarMayusculasNiEspacios() {
        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.agregarNodo(DocumentoLienzo.vacio(),
                null, null, pregunta("¿Fiebre?", false, "Sí", Gravedad.LEVE, "  sí ", Gravedad.GRAVE)));
    }

    @Test
    void unaConclusionSinDesenlaceSeRechaza() {
        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.agregarNodo(
                LienzoFiebre.primeraPregunta(), INICIO, MAS_39, conclusion(null, "Sin desenlace")));
    }

    @Test
    void enlazarFormandoUnCirculoSeRechaza() {
        // DECAIDO --Sí--> ... no vuelve a INICIO; hacer que vuelva formaría un círculo.
        DocumentoLienzo d = EditorLienzo.enlazar(LienzoFiebre.completo(), SI, null);
        EdicionInvalidaException e = assertThrows(EdicionInvalidaException.class,
                () -> EditorLienzo.enlazar(d, SI, INICIO));
        assertTrue(e.getMessage().contains("círculo"));
    }

    @Test
    void enlazarALaMismaPreguntaOAUnaConclusionSeRechaza() {
        DocumentoLienzo d = EditorLienzo.enlazar(LienzoFiebre.completo(), SI, null);

        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.enlazar(d, SI, DECAIDO));
        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.enlazar(d, SI, RECOMENDACION));
    }

    @Test
    void idsInexistentesDan404() {
        DocumentoLienzo d = LienzoFiebre.completo();

        assertThrows(RecursoNoEncontradoException.class, () -> EditorLienzo.enlazar(d, "r_99", DECAIDO));
        assertThrows(RecursoNoEncontradoException.class, () -> EditorLienzo.enlazar(d, SI, "pregunta_99"));
        assertThrows(RecursoNoEncontradoException.class, () -> EditorLienzo.eliminarNodo(d, "pregunta_99"));
        assertThrows(RecursoNoEncontradoException.class, () -> EditorLienzo.agregarNodo(d, DECAIDO, MAS_39,
                conclusion(Desenlace.URGENCIA, "x")));
    }

    @Test
    void editarConservaElDestinoDeLasRespuestasConIdYCreaLasNuevas() {
        DatosNodo cambios = new DatosNodo(null, "¿Está muy decaído?", true, null, List.of(
                new DatosRespuesta(SI, "Sí, mucho", Gravedad.GRAVE),
                new DatosRespuesta(null, "Un poco", Gravedad.MODERADA)));

        DocumentoLienzo d = EditorLienzo.editarNodo(LienzoFiebre.completo(), DECAIDO, cambios);

        NodoLienzo decaido = d.nodos().get(DECAIDO);
        assertEquals("¿Está muy decaído?", decaido.texto());
        assertTrue(decaido.variasRespuestas());
        assertEquals(2, decaido.respuestas().size());
        assertEquals(CONSULTA, respuesta(d, DECAIDO, SI).destino());
        assertEquals(Gravedad.GRAVE, respuesta(d, DECAIDO, SI).gravedad());
        RespuestaLienzo nueva = decaido.respuestas().get(1);
        assertEquals("r_11", nueva.id(), "la secuencia sigue después del último id");
        assertNull(nueva.destino());
        assertFalse(decaido.respuestas().stream().anyMatch(r -> r.id().equals(NO)), "la que no vino se quita");
    }

    @Test
    void editarNoCambiaElTipoNiAceptaRespuestasAjenas() {
        DocumentoLienzo d = LienzoFiebre.completo();

        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.editarNodo(d, DECAIDO,
                conclusion(Desenlace.URGENCIA, "x")));
        assertThrows(EdicionInvalidaException.class, () -> EditorLienzo.editarNodo(d, DECAIDO,
                new DatosNodo(null, "¿?", false, null, List.of(new DatosRespuesta(MAS_39, "ajena", null)))));
    }

    @Test
    void eliminarUnNodoVuelveMasLasRespuestasQueLlegabanYBorraSuRama() {
        DocumentoLienzo d = EditorLienzo.eliminarNodo(LienzoFiebre.completo(), DECAIDO);

        assertNull(respuesta(d, INICIO, MENOS_38).destino());
        assertNull(respuesta(d, INICIO, ENTRE_38_39).destino());
        assertFalse(d.nodos().containsKey(DECAIDO));
        assertFalse(d.nodos().containsKey(CONSULTA), "la rama de la pregunta borrada se va con ella");
        assertFalse(d.nodos().containsKey(RECOMENDACION));
        assertTrue(d.nodos().containsKey(URGENCIA), "lo que tiene otro camino se queda");
    }

    @Test
    void eliminarConservaLosNodosQueYaEstabanSueltos() {
        DocumentoLienzo suelto = EditorLienzo.enlazar(LienzoFiebre.completo(), MAS_39, null);

        DocumentoLienzo d = EditorLienzo.eliminarNodo(suelto, DECAIDO);

        assertTrue(d.nodos().containsKey(URGENCIA));
    }

    @Test
    void eliminarLaPrimeraPreguntaVaciaElLienzo() {
        DocumentoLienzo d = EditorLienzo.eliminarNodo(LienzoFiebre.completo(), INICIO);

        assertNull(d.inicio());
        assertTrue(d.nodos().isEmpty());
        // Los ids no se reutilizan.
        DocumentoLienzo otra = EditorLienzo.agregarNodo(d, null, null, pregunta("¿Otra?", false, "a", null, "b", null));
        assertEquals("pregunta_11", otra.inicio());
    }

    @Test
    void laGravedadAusenteEsLeve() {
        DocumentoLienzo d = EditorLienzo.agregarNodo(DocumentoLienzo.vacio(), null, null,
                pregunta("¿Fiebre?", false, "Sí", null, "No", null));

        assertTrue(d.nodos().get(d.inicio()).respuestas().stream().allMatch(r -> r.gravedad() == Gravedad.LEVE));
    }
}
