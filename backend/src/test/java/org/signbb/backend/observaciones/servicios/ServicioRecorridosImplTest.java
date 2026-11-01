package org.signbb.backend.observaciones.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.modelo.CandadoRecorrido;
import org.signbb.backend.navegacion.modelo.RecorridoOcupadoException;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.controladores.PreguntaDTO;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.controladores.ResultadoDTO;
import org.signbb.backend.observaciones.controladores.ResumenRecorridoDTO;
import org.signbb.backend.observaciones.datos.entidades.RecorridoEntity;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioRecorridos;
import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Pruebas de {@link ServicioRecorridosImpl} con la navegación y el
 * repositorio mockeados: el recorrido queda a nombre del padre, otro padre
 * recibe 404 y el estado se lleva al terminar o al retroceder.
 *
 * <p>
 * MODIFICADO (CU09): también cubre la consulta vencida (410 y estado
 * {@code VENCIDO}) y el reinicio con candado.
 */
@ExtendWith(MockitoExtension.class)
class ServicioRecorridosImplTest {

    private static final UsuarioActual P1 = new UsuarioActual(1L, "Papá Uno", "p1@prueba", Rol.PADRE, null);
    private static final UsuarioActual P2 = new UsuarioActual(2L, "Mamá Dos", "p2@prueba", Rol.PADRE, null);
    private static final String ID = "r-1";

    @Mock
    private ServicioNavegacion navegacion;

    @Mock
    private RepositorioRecorridos recorridos;

    @Mock
    private RepositorioSesion sesiones;

    @Mock
    private PlatformTransactionManager gestor;

    private ServicioRecorridosImpl servicio;

    /** Candado que siempre deja pasar. */
    private static class CandadoLibre implements CandadoRecorrido {
        @Override
        public <T> T conCandado(String recorridoId, Supplier<T> accion) {
            return accion.get();
        }
    }

    @BeforeEach
    void preparar() {
        lenient().when(gestor.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        servicio = crear(new CandadoLibre());
    }

    private ServicioRecorridosImpl crear(CandadoRecorrido candado) {
        return new ServicioRecorridosImpl(navegacion, recorridos, sesiones, candado, gestor);
    }

    private static RecorridoDTO enCurso() {
        return new RecorridoDTO(ID, "EN_CURSO",
                new PreguntaDTO("n1", "¿texto?", List.of("SI", "NO"), false, List.of(), null), null);
    }

    private static RecorridoDTO finalizado() {
        ResultadoDTO resultado = new ResultadoDTO("URGENCIA", "fin", "Vaya a urgencias", List.of(), List.of(),
                List.of());
        return new RecorridoDTO(ID, "FINALIZADO", null, resultado);
    }

    private static RecorridoEntity guardado(Long usuarioId) {
        RecorridoEntity r = new RecorridoEntity();
        r.setId(ID);
        r.setUsuarioId(usuarioId);
        r.setEstado("EN_CURSO");
        r.setIniciadoEn(Instant.now());
        return r;
    }

    @Test
    void iniciarRegistraElRecorridoANombreDelPadre() {
        when(navegacion.iniciar()).thenReturn(enCurso());

        RecorridoDTO dto = servicio.iniciar(P1);

        assertEquals(ID, dto.recorridoId());
        ArgumentCaptor<RecorridoEntity> fila = ArgumentCaptor.forClass(RecorridoEntity.class);
        verify(recorridos).save(fila.capture());
        assertEquals(P1.id(), fila.getValue().getUsuarioId());
        assertEquals("EN_CURSO", fila.getValue().getEstado());
        assertNotNull(fila.getValue().getIniciadoEn());
        assertNull(fila.getValue().getFinalizadoEn());
    }

    @Test
    void elDuenoPasa() {
        when(recorridos.findById(ID)).thenReturn(Optional.of(guardado(P1.id())));

        servicio.exigirDueno(ID, P1);
    }

    @Test
    void otroPadreRecibe404() {
        when(recorridos.findById(ID)).thenReturn(Optional.of(guardado(P1.id())));

        RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.exigirDueno(ID, P2));
        assertEquals(ServicioRecorridosImpl.NO_DISPONIBLE, e.getMessage());
    }

    @Test
    void alTerminarGuardaElResultado() {
        RecorridoEntity fila = guardado(P1.id());
        when(recorridos.findById(ID)).thenReturn(Optional.of(fila));

        servicio.actualizar(finalizado());

        assertEquals("FINALIZADO", fila.getEstado());
        assertEquals("URGENCIA", fila.getNivel());
        assertEquals("Vaya a urgencias", fila.getMensaje());
        assertNotNull(fila.getFinalizadoEn());
        verify(recorridos).save(fila);
    }

    @Test
    void alRetrocederDesdeElFinalVuelveAEnCurso() {
        RecorridoEntity fila = guardado(P1.id());
        when(recorridos.findById(ID)).thenReturn(Optional.of(fila));
        servicio.actualizar(finalizado());

        servicio.actualizar(enCurso());

        assertEquals("EN_CURSO", fila.getEstado());
        assertNull(fila.getNivel());
        assertNull(fila.getMensaje());
        assertNull(fila.getFinalizadoEn());
    }

    @Test
    void listaLosRecorridosDelPadre() {
        when(recorridos.findByUsuarioIdOrderByIniciadoEnDesc(P1.id())).thenReturn(List.of(guardado(P1.id())));
        when(sesiones.existe(ID)).thenReturn(true);

        List<ResumenRecorridoDTO> lista = servicio.deUsuario(P1);

        assertEquals(1, lista.size());
        assertEquals(ID, lista.get(0).recorridoId());
        assertEquals("EN_CURSO", lista.get(0).estado());
    }

    @Test
    void unRecorridoEnCursoSinSesionDa410YQuedaVencido() {
        RecorridoEntity fila = guardado(P1.id());
        when(recorridos.findById(ID)).thenReturn(Optional.of(fila));
        when(sesiones.existe(ID)).thenReturn(false);

        RecorridoVencidoException e = assertThrows(RecorridoVencidoException.class,
                () -> servicio.exigirVigente(ID));

        assertEquals(RecorridoVencidoException.MENSAJE, e.getMessage());
        assertEquals("VENCIDO", fila.getEstado());
        verify(recorridos).save(fila);
    }

    @Test
    void unRecorridoVencidoOReiniciadoDa410ConSuMensaje() {
        RecorridoEntity fila = guardado(P1.id());
        when(recorridos.findById(ID)).thenReturn(Optional.of(fila));

        fila.setEstado("VENCIDO");
        assertEquals(RecorridoVencidoException.MENSAJE,
                assertThrows(RecorridoVencidoException.class, () -> servicio.exigirVigente(ID)).getMessage());
        fila.setEstado("ABANDONADO");
        assertEquals(RecorridoVencidoException.MENSAJE_REINICIADO,
                assertThrows(RecorridoVencidoException.class, () -> servicio.exigirVigente(ID)).getMessage());
    }

    @Test
    void unRecorridoFinalizadoSigueVigenteAunqueSuSesionHayaVencido() {
        RecorridoEntity fila = guardado(P1.id());
        fila.setEstado("FINALIZADO");
        when(recorridos.findById(ID)).thenReturn(Optional.of(fila));

        servicio.exigirVigente(ID);

        assertEquals("FINALIZADO", fila.getEstado());
        verify(sesiones, never()).existe(any());
    }

    @Test
    void reiniciarDejaElViejoAbandonadoBorraSuSesionYEmpiezaUnoNuevo() {
        RecorridoEntity viejo = guardado(P1.id());
        when(recorridos.findById(ID)).thenReturn(Optional.of(viejo));
        RecorridoDTO otro = new RecorridoDTO("r-2", "EN_CURSO", enCurso().pregunta(), null);
        when(navegacion.iniciar()).thenReturn(otro);

        RecorridoDTO nuevo = servicio.reiniciar(ID, P1);

        assertEquals("r-2", nuevo.recorridoId());
        assertEquals("ABANDONADO", viejo.getEstado());
        verify(sesiones).eliminar(ID);
        ArgumentCaptor<RecorridoEntity> filas = ArgumentCaptor.forClass(RecorridoEntity.class);
        verify(recorridos, times(2)).save(filas.capture());
        assertEquals("r-2", filas.getAllValues().get(1).getId());
        assertEquals(P1.id(), filas.getAllValues().get(1).getUsuarioId());
    }

    @Test
    void siElRecorridoEstaOcupadoReiniciarNoTocaNada() {
        servicio = crear(new CandadoRecorrido() {
            @Override
            public <T> T conCandado(String recorridoId, Supplier<T> accion) {
                throw new RecorridoOcupadoException();
            }
        });

        assertThrows(RecorridoOcupadoException.class, () -> servicio.reiniciar(ID, P1));
        verifyNoInteractions(recorridos, sesiones, navegacion);
    }
}
