package org.signbb.backend.autoria.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.servicios.ContenidoInvalidoException;
import org.signbb.backend.autoria.servicios.EdicionInvalidaException;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
import org.signbb.backend.config.ManejadorExcepciones;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests de {@link ControladorAutoria} con MockMvc en modo standalone y el
 * servicio simulado: rutas, códigos HTTP y el cuerpo de los errores.
 */
class ControladorAutoriaTest {

    private ServicioAutoria servicio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(ServicioAutoria.class);
        mvc = MockMvcBuilders.standaloneSetup(new ControladorAutoria(servicio))
                .setControllerAdvice(new ManejadorErroresAutoria(), new ManejadorExcepciones())
                .build();
    }

    private MockHttpServletResponse ejecutar(RequestBuilder peticion) throws Exception {
        return mvc.perform(peticion).andReturn().getResponse();
    }

    private static String cuerpo(MockHttpServletResponse respuesta) throws Exception {
        return respuesta.getContentAsString(StandardCharsets.UTF_8);
    }

    private static SubgrafoDTO vacio() {
        return new SubgrafoDTO(1L, "grafo_fiebre", "Fiebre", "puente_neuro", "neurologico", "Fiebre", null, null,
                EstadoBorrador.BORRADOR, null, List.of(), List.of(), null, null, null, false);
    }

    @Test
    void crearResponde201ConElBorrador() throws Exception {
        when(servicio.crear(any())).thenReturn(vacio());

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Fiebre\",\"puenteId\":\"puente_neuro\"}"));

        assertEquals(201, r.getStatus());
        assertTrue(cuerpo(r).contains("\"grafoId\":\"grafo_fiebre\""));
    }

    @Test
    void elMasPasaLaRespuestaYElTipoAlServicio() throws Exception {
        when(servicio.agregarNodo(eq(1L), any())).thenReturn(vacio());

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/nodos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"despuesDe\":{\"nodoId\":\"pregunta_1\",\"respuestaId\":\"r_4\"},"
                        + "\"tipo\":\"CONCLUSION\",\"texto\":\"Urgencias\",\"desenlace\":\"URGENCIA\"}"));

        assertEquals(200, r.getStatus());
        verify(servicio).agregarNodo(eq(1L), any(NodoRequest.class));
    }

    @Test
    void enlazarConNodoNuloVuelveLaRespuestaUnMas() throws Exception {
        when(servicio.enlazar(1L, "r_2", null)).thenReturn(vacio());

        MockHttpServletResponse r = ejecutar(put("/api/autoria/subgrafos/1/respuestas/r_2/destino")
                .contentType(MediaType.APPLICATION_JSON).content("{\"nodoId\":null}"));

        assertEquals(200, r.getStatus());
        verify(servicio).enlazar(eq(1L), eq("r_2"), isNull());
    }

    @Test
    void unaEdicionInvalidaResponde422ConElMensaje() throws Exception {
        when(servicio.agregarNodo(eq(1L), any()))
                .thenThrow(new EdicionInvalidaException("El subgrafo ya tiene una primera pregunta."));

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/nodos")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"PREGUNTA\",\"texto\":\"x\"}"));

        assertEquals(422, r.getStatus());
        assertTrue(cuerpo(r).contains("\"mensaje\":\"El subgrafo ya tiene una primera pregunta.\""));
        assertTrue(cuerpo(r).contains("\"avisos\":[]"));
    }

    @Test
    void publicarConAvisosResponde422ConLaLista() throws Exception {
        Aviso aviso = new Aviso(Aviso.Codigo.RESPUESTA_SIN_CAMINO, true, "falta un camino", "pregunta_1", "r_2");
        when(servicio.publicar(1L)).thenThrow(new ContenidoInvalidoException("Hay avisos", List.of(aviso)));

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/publicar"));

        assertEquals(422, r.getStatus());
        assertTrue(cuerpo(r).contains("\"codigo\":\"RESPUESTA_SIN_CAMINO\""));
        assertTrue(cuerpo(r).contains("\"respuestaId\":\"r_2\""));
    }

    @Test
    void descartarResponde200ConElBorrador() throws Exception {
        when(servicio.descartarCambios(1L)).thenReturn(vacio());

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/descartar"));

        assertEquals(200, r.getStatus());
        verify(servicio).descartarCambios(1L);
    }

    @Test
    void borrarUnSubgrafoPublicadoResponde422ConElMensaje() throws Exception {
        doThrow(new EdicionInvalidaException(
                "Este subgrafo ya está publicado y las cuidadoras lo usan: no se puede borrar."))
                .when(servicio).eliminar(1L);

        MockHttpServletResponse r = ejecutar(delete("/api/autoria/subgrafos/1"));

        assertEquals(422, r.getStatus());
        assertTrue(cuerpo(r).contains("ya está publicado y las cuidadoras lo usan: no se puede borrar."));
        assertTrue(cuerpo(r).contains("\"avisos\":[]"));
    }

    @Test
    void borrarUnBorradorQueNuncaSePublicoResponde204() throws Exception {
        MockHttpServletResponse r = ejecutar(delete("/api/autoria/subgrafos/1"));

        assertEquals(204, r.getStatus());
        verify(servicio).eliminar(1L);
    }

    @Test
    void borrarUnSubgrafoQueNoExisteResponde404() throws Exception {
        doThrow(new RecursoNoEncontradoException("subgrafo 9")).when(servicio).eliminar(9L);

        assertEquals(404, ejecutar(delete("/api/autoria/subgrafos/9")).getStatus());
    }

    @Test
    void descartarUnSubgrafoQueNoSePuedeVolverAtrasResponde422() throws Exception {
        when(servicio.descartarCambios(1L))
                .thenThrow(new EdicionInvalidaException("No hay una versión publicada a la que volver."));

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/descartar"));

        assertEquals(422, r.getStatus());
        assertTrue(cuerpo(r).contains("No hay una versión publicada a la que volver."));
    }

    @Test
    void unSubgrafoQueNoExisteResponde404() throws Exception {
        when(servicio.obtener(9L)).thenThrow(new RecursoNoEncontradoException("no hay un subgrafo con id 9"));

        assertEquals(404, ejecutar(get("/api/autoria/subgrafos/9")).getStatus());
    }

    @Test
    void dosEdicionesALaVezResponden409() throws Exception {
        when(servicio.eliminarNodo(1L, "pregunta_6"))
                .thenThrow(new ObjectOptimisticLockingFailureException("BorradorSubgrafoEntity", 1L));

        MockHttpServletResponse r = ejecutar(
                delete(
                        "/api/autoria/subgrafos/1/nodos/pregunta_6"));

        assertEquals(409, r.getStatus());
    }
}
