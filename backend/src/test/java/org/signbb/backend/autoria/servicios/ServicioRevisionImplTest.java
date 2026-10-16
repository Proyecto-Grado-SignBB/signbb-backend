package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_A;
import static org.signbb.backend.seguridad.UsuariosPrueba.SUPERVISOR;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.ResumenSubgrafoDTO;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.AccesoDenegadoException;
import org.springframework.data.domain.Sort;

/**
 * Tests de {@link ServicioRevisionImpl} con el repositorio y {@link
 * ServicioAutoria} simulados: transiciones de estado, quién puede aprobar o
 * rechazar, y que aprobar publique.
 */
class ServicioRevisionImplTest {

    /** Otro supervisor que sí es autor del borrador. */
    private static final UsuarioActual SUPERVISOR_AUTOR =
            new UsuarioActual(MEDICO_A.id(), "Dra. A", "a@signbb.local", Rol.MEDICO_SUPERVISOR, null);

    private RepositorioBorradorSubgrafo borradores;
    private ServicioAutoria autoria;
    private ServicioRevisionImpl servicio;
    private BorradorSubgrafoEntity b;

    @BeforeEach
    void preparar() {
        borradores = mock(RepositorioBorradorSubgrafo.class);
        autoria = mock(ServicioAutoria.class);
        when(autoria.avisos(1L)).thenReturn(List.of());
        b = LienzoFiebre.borrador(LienzoFiebre.completo());
        b.setAutorId(MEDICO_A.id());
        when(borradores.findById(1L)).thenReturn(Optional.of(b));
        servicio = new ServicioRevisionImpl(borradores, autoria);
    }

    @Test
    void enviarLoDejaEnRevisionYBorraElComentarioAnterior() {
        b.setComentarioRevision("Corrige la conclusión");

        servicio.enviar(1L);

        assertEquals(EstadoBorrador.EN_REVISION, b.getEstado());
        assertNotNull(b.getEnviadoRevisionEn());
        assertNull(b.getComentarioRevision());
        verify(borradores).save(b);
    }

    @Test
    void enviarConAvisosBloqueantesNoCambiaNada() {
        Aviso bloqueante = new Aviso(Aviso.Codigo.RESPUESTA_SIN_CAMINO, true, "falta", "pregunta_1", "r_1");
        when(autoria.avisos(1L)).thenReturn(List.of(bloqueante));

        ContenidoInvalidoException e = assertThrows(ContenidoInvalidoException.class, () -> servicio.enviar(1L));

        assertEquals(List.of(bloqueante), e.getAvisos());
        assertEquals(EstadoBorrador.BORRADOR, b.getEstado());
        verify(borradores, never()).save(any());
    }

    @Test
    void noSeEnviaDosVecesNiAlgoSinCambios() {
        b.setEstado(EstadoBorrador.EN_REVISION);
        assertThrows(EdicionInvalidaException.class, () -> servicio.enviar(1L));
        b.setEstado(EstadoBorrador.PUBLICADO);
        assertThrows(EdicionInvalidaException.class, () -> servicio.enviar(1L));
    }

    @Test
    void retirarVuelveABorradorOACambiosSinPublicar() {
        b.setEstado(EstadoBorrador.EN_REVISION);
        servicio.retirar(1L);
        assertEquals(EstadoBorrador.BORRADOR, b.getEstado());

        b.setEstado(EstadoBorrador.EN_REVISION);
        b.setPublicadoEn(Instant.now());
        servicio.retirar(1L);
        assertEquals(EstadoBorrador.CAMBIOS_SIN_PUBLICAR, b.getEstado());
    }

    @Test
    void retirarAlgoQueNoEstaEnRevisionEs422() {
        assertThrows(EdicionInvalidaException.class, () -> servicio.retirar(1L));
    }

    @Test
    void aprobarPublicaYGuardaElRevisor() {
        b.setEstado(EstadoBorrador.EN_REVISION);

        servicio.aprobar(1L, SUPERVISOR);

        verify(autoria).publicar(1L);
        assertEquals(SUPERVISOR.id(), b.getRevisorId());
        assertNotNull(b.getRevisadoEn());
        assertNull(b.getComentarioRevision());
    }

    @Test
    void nadieApruebaLoSuyo() {
        b.setEstado(EstadoBorrador.EN_REVISION);

        assertThrows(AccesoDenegadoException.class, () -> servicio.aprobar(1L, SUPERVISOR_AUTOR));
        verify(autoria, never()).publicar(any());
    }

    @Test
    void aprobarAlgoQueNoEstaEnRevisionEs422() {
        assertThrows(EdicionInvalidaException.class, () -> servicio.aprobar(1L, SUPERVISOR));
        verify(autoria, never()).publicar(any());
    }

    @Test
    void rechazarSinComentarioEs422YNoCambiaElEstado() {
        b.setEstado(EstadoBorrador.EN_REVISION);

        assertThrows(EdicionInvalidaException.class, () -> servicio.rechazar(1L, "  ", SUPERVISOR));
        assertThrows(EdicionInvalidaException.class, () -> servicio.rechazar(1L, null, SUPERVISOR));
        assertEquals(EstadoBorrador.EN_REVISION, b.getEstado());
    }

    @Test
    void rechazarDevuelveElBorradorConElComentario() {
        b.setEstado(EstadoBorrador.EN_REVISION);

        servicio.rechazar(1L, "  Falta la conclusión de fiebre baja ", SUPERVISOR);

        assertEquals(EstadoBorrador.BORRADOR, b.getEstado());
        assertEquals("Falta la conclusión de fiebre baja", b.getComentarioRevision());
        assertEquals(SUPERVISOR.id(), b.getRevisorId());
        verify(autoria, never()).publicar(any());
    }

    @Test
    void nadieRechazaLoSuyo() {
        b.setEstado(EstadoBorrador.EN_REVISION);

        assertThrows(AccesoDenegadoException.class, () -> servicio.rechazar(1L, "no", SUPERVISOR_AUTOR));
    }

    @Test
    void lasPendientesExcluyenLasPropias() {
        BorradorSubgrafoEntity deOtro = LienzoFiebre.borrador(LienzoFiebre.completo());
        deOtro.setId(2L);
        deOtro.setAutorId(99L);
        when(borradores.findByEstado(any(), any(Sort.class))).thenReturn(List.of(b, deOtro));

        List<ResumenSubgrafoDTO> pendientes = servicio.pendientes(SUPERVISOR_AUTOR);

        assertEquals(List.of(2L), pendientes.stream().map(ResumenSubgrafoDTO::id).toList());
    }
}
