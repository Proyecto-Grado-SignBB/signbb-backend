package org.signbb.backend.observaciones.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.config.ManejadorErroresGenerales;
import org.signbb.backend.config.ManejadorExcepciones;
import org.signbb.backend.navegacion.modelo.RecorridoOcupadoException;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.servicios.PreguntaDesactualizadaException;
import org.signbb.backend.observaciones.servicios.ServicioCaptura;
import org.signbb.backend.observaciones.servicios.ServicioRecorridos;
import org.signbb.backend.seguridad.UsuariosPrueba;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests de {@link ControladorConsulta} con MockMvc en modo standalone y los
 * servicios simulados: la respuesta del cuidador vacía o fuera de los límites
 * se rechaza antes de llegar al servicio (nunca un 500).
 */
class ControladorConsultaTest {

    private ServicioCaptura captura;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        captura = mock(ServicioCaptura.class);
        ControladorConsulta controlador = new ControladorConsulta();
        ReflectionTestUtils.setField(controlador, "servicioNavegacion", mock(ServicioNavegacion.class));
        ReflectionTestUtils.setField(controlador, "servicioCaptura", captura);
        ReflectionTestUtils.setField(controlador, "servicioRecorridos", mock(ServicioRecorridos.class));
        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setCustomArgumentResolvers(UsuariosPrueba.resolvedor(UsuariosPrueba.MEDICO_A))
                .setControllerAdvice(new ManejadorExcepciones(), new ManejadorErroresGenerales())
                .build();
    }

    private MockHttpServletResponse responder(String json) throws Exception {
        return mvc.perform(post("/api/recorridos/r1/respuestas").contentType(MediaType.APPLICATION_JSON)
                .content(json)).andReturn().getResponse();
    }

    @Test
    void sinRespuestaNiOpcionesDa400YNoLlegaAlServicio() throws Exception {
        assertEquals(400, responder("{}").getStatus());
        verifyNoInteractions(captura);
    }

    @Test
    void unaListaDeOpcionesVaciaDa400YNoLlegaAlServicio() throws Exception {
        assertEquals(400, responder("{\"opciones\":[]}").getStatus());
        verifyNoInteractions(captura);
    }

    @Test
    void unaRespuestaValidaLlegaAlServicioComoUnaLista() throws Exception {
        assertEquals(200, responder("{\"respuesta\":\"SI\"}").getStatus());

        verify(captura).registrarRespuesta(eq("r1"), any(), eq(List.of("SI")), anyLong(), any());
    }

    @Test
    void unaRespuestaDeMasDe200CaracteresDa422ConMensaje() throws Exception {
        MockHttpServletResponse r = responder("{\"respuesta\":\"" + "a".repeat(201) + "\"}");

        assertEquals(422, r.getStatus());
        assertTrue(r.getContentAsString(StandardCharsets.UTF_8).contains("200 caracteres"));
        verifyNoInteractions(captura);
    }

    @Test
    void siElRecorridoEstaOcupadoResponder409ConMensaje() throws Exception {
        when(captura.registrarRespuesta(eq("r1"), any(), eq(List.of("SI")), anyLong(), any()))
                .thenThrow(new RecorridoOcupadoException());

        MockHttpServletResponse r = responder("{\"respuesta\":\"SI\"}");

        assertEquals(409, r.getStatus());
        assertTrue(r.getContentAsString(StandardCharsets.UTF_8).contains("Espera un momento"));
    }

    @Test
    void elIdDeLaPreguntaLlegaAlServicio() throws Exception {
        assertEquals(200, responder("{\"respuesta\":\"SI\",\"idPregunta\":\"n1\"}").getStatus());

        verify(captura).registrarRespuesta(eq("r1"), any(), eq(List.of("SI")), anyLong(), eq("n1"));
    }

    @Test
    void siLaPreguntaYaNoEsLaActualResponder409ConMensaje() throws Exception {
        when(captura.registrarRespuesta(eq("r1"), any(), eq(List.of("SI")), anyLong(), eq("n1")))
                .thenThrow(new PreguntaDesactualizadaException());

        MockHttpServletResponse r = responder("{\"respuesta\":\"SI\",\"idPregunta\":\"n1\"}");

        assertEquals(409, r.getStatus());
        assertTrue(r.getContentAsString(StandardCharsets.UTF_8).contains("ya fue respondida"));
    }

    @Test
    void retrocederPasaPorElServicioDeCaptura() throws Exception {
        MockHttpServletResponse r = mvc.perform(post("/api/recorridos/r1/retroceder")).andReturn().getResponse();

        assertEquals(200, r.getStatus());
        verify(captura).retroceder("r1");
    }

    @Test
    void siElRecorridoEstaOcupadoRetrocederDa409() throws Exception {
        when(captura.retroceder("r1")).thenThrow(new RecorridoOcupadoException());

        MockHttpServletResponse r = mvc.perform(post("/api/recorridos/r1/retroceder")).andReturn().getResponse();

        assertEquals(409, r.getStatus());
    }
}
