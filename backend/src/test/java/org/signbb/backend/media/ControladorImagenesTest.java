package org.signbb.backend.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.ManejadorErroresAutoria;
import org.signbb.backend.config.ManejadorExcepciones;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Tests de {@link ControladorImagenes} con MockMvc standalone y el servicio simulado. */
class ControladorImagenesTest {

    private static final String NOMBRE = "11111111-1111-1111-1111-111111111111.png";

    private ServicioImagenes servicio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(ServicioImagenes.class);
        mvc = MockMvcBuilders.standaloneSetup(new ControladorImagenes(servicio))
                .setControllerAdvice(new ManejadorErroresAutoria(), new ManejadorExcepciones())
                .build();
    }

    private static String cuerpo(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void subirResponde201ConElNombre() throws Exception {
        byte[] datos = { 1, 2, 3 };
        when(servicio.subir(datos)).thenReturn(NOMBRE);

        MockHttpServletResponse r = mvc.perform(multipart("/api/autoria/imagenes")
                .file(new MockMultipartFile("archivo", "foto.png", "image/png", datos))).andReturn().getResponse();

        assertEquals(201, r.getStatus());
        assertTrue(cuerpo(r).contains(NOMBRE));
    }

    @Test
    void subirUnaImagenInvalidaResponde422ConElMensaje() throws Exception {
        when(servicio.subir(new byte[] { 1 })).thenThrow(new ImagenInvalidaException("Solo se aceptan imágenes."));

        MockHttpServletResponse r = mvc.perform(multipart("/api/autoria/imagenes")
                .file(new MockMultipartFile("archivo", "x.txt", "text/plain", new byte[] { 1 }))).andReturn()
                .getResponse();

        assertEquals(422, r.getStatus());
        assertTrue(cuerpo(r).contains("Solo se aceptan imágenes."));
    }

    @Test
    void siSiloNoRespondeSubirDa503() throws Exception {
        when(servicio.subir(new byte[] { 1 })).thenThrow(new AlmacenNoDisponibleException("x", new RuntimeException()));

        MockHttpServletResponse r = mvc.perform(multipart("/api/autoria/imagenes")
                .file(new MockMultipartFile("archivo", "x.png", "image/png", new byte[] { 1 }))).andReturn()
                .getResponse();

        assertEquals(503, r.getStatus());
    }

    @Test
    void verEntregaLosBytesConSuTipoYCache() throws Exception {
        byte[] datos = { 9, 8, 7 };
        when(servicio.obtener(NOMBRE)).thenReturn(new AlmacenImagenes.Imagen(datos, "image/png"));

        MockHttpServletResponse r = mvc.perform(get("/media/" + NOMBRE)).andReturn().getResponse();

        assertEquals(200, r.getStatus());
        assertEquals("image/png", r.getContentType());
        assertArrayEquals(datos, r.getContentAsByteArray());
        assertTrue(r.getHeader("Cache-Control").contains("immutable"));
    }

    @Test
    void verUnaImagenQueNoExisteDa404() throws Exception {
        when(servicio.obtener(NOMBRE)).thenThrow(new RecursoNoEncontradoException("no hay"));

        assertEquals(404, mvc.perform(get("/media/" + NOMBRE)).andReturn().getResponse().getStatus());
    }
}
