package org.signbb.backend.autoria.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.servicios.ContenidoInvalidoException;
import org.signbb.backend.autoria.servicios.EdicionInvalidaException;
import org.signbb.backend.autoria.servicios.GuardiaAutoria;
import org.signbb.backend.autoria.servicios.ServicioRevision;
import org.signbb.backend.config.ManejadorExcepciones;
import org.signbb.backend.seguridad.UsuariosPrueba;
import org.signbb.backend.seguridad.controladores.ManejadorErroresSeguridad;
import org.signbb.backend.seguridad.servicios.AccesoDenegadoException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests de {@link ControladorRevision} con MockMvc standalone, el servicio y
 * la guardia simulados: rutas, códigos HTTP y que el usuario llegue al
 * servicio. Los roles los exige {@code ConfiguracionSeguridad} y se prueban en
 * {@code ReglasAccesoIntegracionTest}.
 */
class ControladorRevisionTest {

    private ServicioRevision servicio;
    private GuardiaAutoria guardia;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(ServicioRevision.class);
        guardia = mock(GuardiaAutoria.class);
        mvc = MockMvcBuilders.standaloneSetup(new ControladorRevision(servicio, guardia))
                .setCustomArgumentResolvers(UsuariosPrueba.resolvedor(UsuariosPrueba.SUPERVISOR))
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

    private static SubgrafoDTO en(EstadoBorrador estado, String comentario) {
        return new SubgrafoDTO(1L, "grafo_fiebre", "Fiebre", "puente_neuro", "neurologico", "Fiebre", "Dra. A",
                null, estado, null, List.of(), List.of(), null, null, null, false, 10L, comentario, null);
    }

    @Test
    void enviarConAvisosResponde422ConLaLista() throws Exception {
        Aviso aviso = new Aviso(Aviso.Codigo.RESPUESTA_SIN_CAMINO, true, "falta un camino", "pregunta_1", "r_2");
        when(servicio.enviar(1L)).thenThrow(new ContenidoInvalidoException("Hay avisos", List.of(aviso)));

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/enviar-revision"));

        assertEquals(422, r.getStatus());
        assertTrue(cuerpo(r).contains("\"codigo\":\"RESPUESTA_SIN_CAMINO\""));
        assertTrue(cuerpo(r).contains("\"respuestaId\":\"r_2\""));
    }

    @Test
    void enviarRevisaQueSeaElAutor() throws Exception {
        doThrow(new AccesoDenegadoException("Solo el autor puede editar este subgrafo."))
                .when(guardia).autorSubgrafo(1L, UsuariosPrueba.SUPERVISOR);

        assertEquals(403, ejecutar(post("/api/autoria/subgrafos/1/enviar-revision")).getStatus());
        verify(servicio, never()).enviar(any());
    }

    @Test
    void retirarResponde200() throws Exception {
        when(servicio.retirar(1L)).thenReturn(en(EstadoBorrador.BORRADOR, null));

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/retirar-revision"));

        assertEquals(200, r.getStatus());
        verify(guardia).autorSubgrafo(1L, UsuariosPrueba.SUPERVISOR);
    }

    @Test
    void aprobarPasaElSupervisorYDevuelveElPublicado() throws Exception {
        when(servicio.aprobar(1L, UsuariosPrueba.SUPERVISOR)).thenReturn(en(EstadoBorrador.PUBLICADO, null));

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/aprobar"));

        assertEquals(200, r.getStatus());
        assertTrue(cuerpo(r).contains("\"estado\":\"PUBLICADO\""));
    }

    @Test
    void aprobarLoPropioResponde403() throws Exception {
        when(servicio.aprobar(1L, UsuariosPrueba.SUPERVISOR))
                .thenThrow(new AccesoDenegadoException("No puedes revisar un subgrafo tuyo."));

        assertEquals(403, ejecutar(post("/api/autoria/subgrafos/1/aprobar")).getStatus());
    }

    @Test
    void rechazarPasaElComentario() throws Exception {
        when(servicio.rechazar(1L, "Falta la conclusión de fiebre baja", UsuariosPrueba.SUPERVISOR))
                .thenReturn(en(EstadoBorrador.BORRADOR, "Falta la conclusión de fiebre baja"));

        MockHttpServletResponse r = ejecutar(post("/api/autoria/subgrafos/1/rechazar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comentario\":\"Falta la conclusión de fiebre baja\"}"));

        assertEquals(200, r.getStatus());
        assertTrue(cuerpo(r).contains("\"comentarioRevision\":\"Falta la conclusión de fiebre baja\""));
    }

    @Test
    void rechazarSinCuerpoResponde422() throws Exception {
        when(servicio.rechazar(1L, null, UsuariosPrueba.SUPERVISOR))
                .thenThrow(new EdicionInvalidaException("Escribe un comentario."));

        assertEquals(422, ejecutar(post("/api/autoria/subgrafos/1/rechazar")).getStatus());
    }

    @Test
    void lasPendientesSonDelSupervisor() throws Exception {
        when(servicio.pendientes(UsuariosPrueba.SUPERVISOR)).thenReturn(List.of());

        assertEquals(200, ejecutar(get("/api/autoria/revisiones")).getStatus());
        verify(servicio).pendientes(UsuariosPrueba.SUPERVISOR);
    }
}
