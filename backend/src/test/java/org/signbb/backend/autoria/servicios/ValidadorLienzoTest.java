package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.CONSULTA;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.DECAIDO;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.INICIO;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.MAS_39;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.SI;
import static org.signbb.backend.autoria.servicios.LienzoFiebre.URGENCIA;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.Aviso.Codigo;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;

/** Tests de {@link ValidadorLienzo}: un caso por aviso. */
class ValidadorLienzoTest {

    private static List<Codigo> codigos(DocumentoLienzo d) {
        return ValidadorLienzo.validar(d).stream().map(Aviso::codigo).toList();
    }

    /** Reemplaza un nodo sin pasar por el editor (para documentos que el editor no deja armar). */
    private static DocumentoLienzo con(DocumentoLienzo d, NodoLienzo nodo) {
        Map<String, NodoLienzo> nodos = new LinkedHashMap<>(d.nodos());
        nodos.put(nodo.id(), nodo);
        return new DocumentoLienzo(d.inicio(), d.secuencia(), nodos);
    }

    @Test
    void unLienzoCompletoNoTieneAvisos() {
        assertEquals(List.of(), ValidadorLienzo.validar(LienzoFiebre.completo()));
    }

    @Test
    void unLienzoVacioNoTienePrimeraPregunta() {
        List<Aviso> avisos = ValidadorLienzo.validar(DocumentoLienzo.vacio());

        assertEquals(List.of(Codigo.SIN_INICIO), avisos.stream().map(Aviso::codigo).toList());
        assertTrue(ValidadorLienzo.hayBloqueantes(avisos));
    }

    @Test
    void cadaMasPendienteEsUnaRespuestaSinCaminoConSuTexto() {
        List<Aviso> avisos = ValidadorLienzo.validar(LienzoFiebre.primeraPregunta());

        assertEquals(3, avisos.size());
        assertTrue(avisos.stream().allMatch(a -> a.codigo() == Codigo.RESPUESTA_SIN_CAMINO && a.bloqueante()));
        Aviso masDe39 = avisos.get(2);
        assertEquals(MAS_39, masDe39.respuestaId());
        assertEquals("La respuesta \"Más de 39 °C\" de \"¿Cuánto marca el termómetro?\" todavía no dice "
                + "qué pasa después.", masDe39.mensaje());
    }

    @Test
    void unaPreguntaConUnaSolaRespuestaPideAlMenosDos() {
        NodoLienzo decaido = LienzoFiebre.completo().nodos().get(DECAIDO);
        DocumentoLienzo d = con(LienzoFiebre.completo(), decaido.conRespuestas(decaido.respuestas().subList(0, 1)));

        assertTrue(codigos(d).contains(Codigo.POCAS_RESPUESTAS));
    }

    @Test
    void textosVaciosSeAvisan() {
        DocumentoLienzo completo = LienzoFiebre.completo();
        NodoLienzo decaido = completo.nodos().get(DECAIDO);
        NodoLienzo sinTexto = new NodoLienzo(DECAIDO, TipoNodo.PREGUNTA, " ", false, null,
                List.of(decaido.respuestas().get(0), new RespuestaLienzo("r_8", "", null, CONSULTA)));

        List<Aviso> avisos = ValidadorLienzo.validar(con(completo, sinTexto));

        assertEquals(2, avisos.stream().filter(a -> a.codigo() == Codigo.TEXTO_VACIO).count());
    }

    @Test
    void respuestasRepetidasGuardadasPorOtraViaSeAvisan() {
        DocumentoLienzo completo = LienzoFiebre.completo();
        NodoLienzo decaido = completo.nodos().get(DECAIDO);
        NodoLienzo repetida = decaido.conRespuestas(List.of(decaido.respuestas().get(0),
                new RespuestaLienzo("r_8", "SÍ", null, CONSULTA)));

        assertTrue(codigos(con(completo, repetida)).contains(Codigo.RESPUESTA_REPETIDA));
    }

    @Test
    void unaConclusionSinDesenlaceSeAvisa() {
        DocumentoLienzo completo = LienzoFiebre.completo();
        NodoLienzo urgencia = completo.nodos().get(URGENCIA);
        NodoLienzo sinDesenlace = new NodoLienzo(URGENCIA, TipoNodo.CONCLUSION, urgencia.texto(), false, null,
                List.of());

        assertEquals(List.of(Codigo.SIN_DESENLACE), codigos(con(completo, sinDesenlace)));
    }

    @Test
    void unCirculoGuardadoPorOtraViaSeAvisa() {
        DocumentoLienzo completo = LienzoFiebre.completo();
        NodoLienzo decaido = completo.nodos().get(DECAIDO);
        NodoLienzo vuelve = decaido.conRespuestas(decaido.respuestas().stream()
                .map(r -> r.id().equals(SI) ? r.conDestino(INICIO) : r).toList());

        List<Aviso> avisos = ValidadorLienzo.validar(con(completo, vuelve));

        Aviso ciclo = avisos.stream().filter(a -> a.codigo() == Codigo.CICLO).findFirst().orElseThrow();
        assertEquals(SI, ciclo.respuestaId());
        assertTrue(ciclo.mensaje().contains("vuelve a \"¿Cuánto marca el termómetro?\""));
    }

    @Test
    void unNodoSueltoAvisaSinBloquearYNoSeRevisaPorDentro() {
        // La conclusión de urgencia queda suelta: su respuesta vuelve a ser un "+".
        DocumentoLienzo d = EditorLienzo.enlazar(LienzoFiebre.completo(), MAS_39, null);
        // Aunque esté suelta y le falte el desenlace, solo da el aviso de suelto.
        NodoLienzo urgencia = d.nodos().get(URGENCIA);
        d = con(d, new NodoLienzo(URGENCIA, TipoNodo.CONCLUSION, urgencia.texto(), false, null, List.of()));

        List<Aviso> avisos = ValidadorLienzo.validar(d);

        Aviso suelto = avisos.stream().filter(a -> a.codigo() == Codigo.NODO_SUELTO).findFirst().orElseThrow();
        assertFalse(suelto.bloqueante());
        assertEquals(URGENCIA, suelto.nodoId());
        assertFalse(avisos.stream().anyMatch(a -> a.codigo() == Codigo.SIN_DESENLACE));
        assertTrue(avisos.stream().anyMatch(a -> a.codigo() == Codigo.RESPUESTA_SIN_CAMINO));
    }

    @Test
    void elChoqueDeGravedadesNoSeAvisa() {
        // "Menos de 38" (LEVE) y "Entre 38 y 39" (MODERADA) llegan a la misma pregunta.
        DocumentoLienzo d = LienzoFiebre.completo();

        assertFalse(ValidadorLienzo.hayBloqueantes(ValidadorLienzo.validar(d)));
        assertTrue(ValidadorLienzo.validar(d).isEmpty());
    }
}
