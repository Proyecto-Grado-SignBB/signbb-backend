package org.signbb.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests de {@link ManejadorErroresGenerales} con MockMvc en modo standalone y
 * un controlador de prueba que lanza los errores representativos: el código
 * HTTP, el cuerpo {@code {mensaje, avisos}} y que nunca se filtre el texto
 * técnico.
 */
class ManejadorErroresGeneralesTest {

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new ManejadorErroresGenerales())
                .build();
    }

    private MockHttpServletResponse ejecutar(RequestBuilder peticion) throws Exception {
        return mvc.perform(peticion).andReturn().getResponse();
    }

    private static String cuerpo(MockHttpServletResponse respuesta) throws Exception {
        return respuesta.getContentAsString(StandardCharsets.UTF_8);
    }

    private static void assertCuerpoDeError(MockHttpServletResponse respuesta) throws Exception {
        assertTrue(respuesta.getContentType().startsWith("application/json"));
        assertTrue(cuerpo(respuesta).contains("\"mensaje\""));
        assertTrue(cuerpo(respuesta).contains("\"avisos\":[]"));
    }

    @Test
    void unJsonMalFormadoDa400ConMensaje() throws Exception {
        MockHttpServletResponse r = ejecutar(post("/prueba/cuerpo").contentType(MediaType.APPLICATION_JSON)
                .content("{"));

        assertEquals(400, r.getStatus());
        assertCuerpoDeError(r);
    }

    @Test
    void unMetodoNoPermitidoDa405ConLaCabeceraAllow() throws Exception {
        MockHttpServletResponse r = ejecutar(delete("/prueba/numero"));

        assertEquals(405, r.getStatus());
        assertNotNull(r.getHeader("Allow"));
        assertTrue(r.getHeader("Allow").contains("GET"));
        assertCuerpoDeError(r);
    }

    @Test
    void unChoqueConLaBaseDeDatosDa409SinMostrarLaRestriccion() throws Exception {
        MockHttpServletResponse r = ejecutar(get("/prueba/conflicto"));

        assertEquals(409, r.getStatus());
        assertCuerpoDeError(r);
        assertFalse(cuerpo(r).contains("uk_usuario_correo"));
    }

    @Test
    void unErrorInesperadoDa500ConMensajeGenericoSinTextoInterno() throws Exception {
        MockHttpServletResponse r = ejecutar(get("/prueba/inesperado"));

        assertEquals(500, r.getStatus());
        assertCuerpoDeError(r);
        assertTrue(cuerpo(r).contains(ManejadorErroresGenerales.MENSAJE_INESPERADO));
        assertFalse(cuerpo(r).contains("secreto interno"));
    }

    /** Controlador mínimo que provoca los errores representativos. */
    @RestController
    @RequestMapping("/prueba")
    static class ControladorDePrueba {

        @PostMapping("/cuerpo")
        String cuerpo(@RequestBody Map<String, Object> datos) {
            return "ok";
        }

        @GetMapping("/numero")
        String numero(@RequestParam("n") int n) {
            return "ok";
        }

        @GetMapping("/conflicto")
        String conflicto() {
            throw new DataIntegrityViolationException("uk_usuario_correo");
        }

        @GetMapping("/inesperado")
        String inesperado() {
            throw new IllegalStateException("secreto interno");
        }
    }
}
