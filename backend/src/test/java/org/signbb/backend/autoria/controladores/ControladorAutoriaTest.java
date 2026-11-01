package org.signbb.backend.autoria.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.servicios.ContenidoInvalidoException;
import org.signbb.backend.autoria.servicios.EdicionInvalidaException;
import org.signbb.backend.autoria.servicios.GuardiaAutoria;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
import org.signbb.backend.config.ManejadorExcepciones;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.seguridad.UsuariosPrueba;
import org.signbb.backend.seguridad.controladores.ManejadorErroresSeguridad;
import org.signbb.backend.seguridad.servicios.AccesoDenegadoException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests de {@link ControladorAutoria} con MockMvc en modo standalone y el
 * servicio simulado: rutas, códigos HTTP y el cuerpo de los errores. La
 * petición llega siempre como {@link UsuariosPrueba#MEDICO_A}.
 */
class ControladorAutoriaTest {

    private ServicioAutoria servicio;
    private GuardiaAutoria guardia;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(ServicioAutoria.class);
        guardia = mock(GuardiaAutoria.class);
        mvc = MockMvcBuilders.standaloneSetup(new ControladorAutoria(servicio, guardia))
                .setCustomArgumentResolvers(UsuariosPrueba.resolvedor(UsuariosPrueba.MEDICO_A))
                .setControllerAdvice(new ManejadorErroresAutoria(), new ManejadorErroresSeguridad(),
                        new ManejadorExcepciones())
                .build();
    }

    private MockHttpServletResponse ejecutar(RequestBuilder peticion) throws Exception {
        return mvc.perform(peticion).andReturn().getResponse();
    }

    private static String cuerpo(MockHttpServletResponse respuesta) throws Exception {
        return respuesta.getContentAsString(StandardCharsets.UTF_8);
    }

    private static SubgrafoDTO vacio() {
        return new SubgrafoDTO(1L, "grafo_fiebre", "Fiebre", "puente_neuro", "Actividad y color", "Fiebre", null, null,
                EstadoBorrador.BORRADOR, null, List.of(), List.of(), null, null, null, false, 10L, null, null);
    }

    @Test
    void crearResponde201ConElBorradorYPasaElUsuario() throws Exception {
        when(servicio.crear(any(), eq(UsuariosPrueba.MEDICO_A))).thenReturn(vacio());

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Fiebre\",\"puenteId\":\"puente_neuro\"}"));

        assertEquals(201, r.getStatus());
        assertTrue(cuerpo(r).contains("\"grafoId\":\"grafo_fiebre\""));
        assertTrue(cuerpo(r).contains("\"autorId\":10"));
    }

    @Test
    void editarLoDeOtroResponde403SinTocarElServicio() throws Exception {
        doThrow(new AccesoDenegadoException("Solo el autor puede editar este subgrafo."))
                .when(guardia).editarSubgrafo(1L, UsuariosPrueba.MEDICO_A);

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/nodos")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"PREGUNTA\"}"));

        assertEquals(403, r.getStatus());
        assertTrue(cuerpo(r).contains("Solo el autor"));
        verify(servicio, never()).agregarNodo(any(), any());
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
    void borrarUnBorradorQueNuncaSePublicoResponde204() throws Exception {
        MockHttpServletResponse r = ejecutar(delete("/api/autoria/subgrafos/1"));

        assertEquals(204, r.getStatus());
        verify(servicio).eliminar(1L);
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
