package org.signbb.backend.observaciones.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.signbb.backend.navegacion.modelo.CandadoRecorrido;
import org.signbb.backend.navegacion.modelo.RecorridoOcupadoException;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.controladores.PreguntaDTO;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.datos.entidades.ObservacionEntity;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioObservaciones;
import org.signbb.backend.observaciones.modelo.TipoInteraccion;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Pruebas de {@link ServicioCapturaImpl} con la navegación, los repositorios y
 * el gestor de transacciones simulados: validación de la respuesta, y que
 * guardar sea "todo o nada" (si algo falla, Postgres revierte y la sesión de
 * Redis vuelve a como estaba).
 */
@ExtendWith(MockitoExtension.class)
class ServicioCapturaImplTest {

    @Mock
    private ServicioNavegacion servicioNavegacion;

    @Mock
    private RepositorioObservaciones repositorioObservaciones;

    @Mock
    private ServicioRecorridos servicioRecorridos;

    @Mock
    private RepositorioSesion repositorioSesion;

    @Mock
    private PlatformTransactionManager gestor;

    @Mock
    private SesionTriaje antes;

    private ServicioCapturaImpl servicioCaptura;

    private static final String RECORRIDO_ID = "r1";
    private static final Long PADRE = 7L;

    /** Candado que siempre deja pasar. */
    private static class CandadoLibre implements CandadoRecorrido {
        @Override
        public <T> T conCandado(String recorridoId, Supplier<T> accion) {
            return accion.get();
        }
    }

    /** Candado que ya tiene otra operación. */
    private static class CandadoTomado implements CandadoRecorrido {
        @Override
        public <T> T conCandado(String recorridoId, Supplier<T> accion) {
            throw new RecorridoOcupadoException();
        }
    }

    @BeforeEach
    void setUp() {
        lenient().when(gestor.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(servicioRecorridos.actualizar(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repositorioSesion.buscar(RECORRIDO_ID)).thenReturn(antes);
        servicioCaptura = crear(new CandadoLibre());
    }

    private ServicioCapturaImpl crear(CandadoRecorrido candado) {
        return new ServicioCapturaImpl(servicioNavegacion, repositorioObservaciones, servicioRecorridos,
                repositorioSesion, candado, gestor);
    }

    private RecorridoDTO recorridoConPregunta(String idNodo, List<String> opciones) {
        PreguntaDTO pregunta = new PreguntaDTO(idNodo, "¿texto?", opciones, false, List.of(), null);
        return new RecorridoDTO(RECORRIDO_ID, "EN_CURSO", pregunta, null);
    }

    @Test
    void reenviaUnaSolaOpcionYAuditaLaObservacion() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n1", List.of("SI", "NO")));
        RecorridoDTO esperado = recorridoConPregunta("n2", List.of("SI", "NO"));
        when(servicioNavegacion.responder(RECORRIDO_ID, List.of("SI"))).thenReturn(esperado);

        RecorridoDTO resultado = servicioCaptura.registrarRespuesta(RECORRIDO_ID, TipoInteraccion.SI_NO,
                List.of("SI"), PADRE);

        assertEquals(esperado, resultado);
        verify(servicioNavegacion).responder(RECORRIDO_ID, List.of("SI"));
        verify(repositorioObservaciones).save(any(ObservacionEntity.class));
    }

    @Test
    void reenviaVariasOpcionesSinResolverCualDomina() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n1", List.of("A", "B", "C")));
        RecorridoDTO esperado = recorridoConPregunta("n2", List.of("SI", "NO"));
        when(servicioNavegacion.responder(eq(RECORRIDO_ID), eq(List.of("A", "B")))).thenReturn(esperado);

        RecorridoDTO resultado = servicioCaptura.registrarRespuesta(RECORRIDO_ID,
                TipoInteraccion.SELECCION_MULTIPLE, List.of("A", "B"), PADRE);

        assertEquals(esperado, resultado);
        // Captura nunca decide cuál gana: siempre reenvía la lista completa tal cual.
        verify(servicioNavegacion).responder(RECORRIDO_ID, List.of("A", "B"));
    }

    @Test
    void rechazaOpcionQueNoEstaEnLaPreguntaActual() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n1", List.of("SI", "NO")));

        assertThrows(IllegalArgumentException.class, () -> servicioCaptura
                .registrarRespuesta(RECORRIDO_ID, TipoInteraccion.SI_NO, List.of("TAL_VEZ"), PADRE));

        verify(servicioNavegacion, never()).responder(anyString(), any(List.class));
        verify(repositorioObservaciones, never()).save(any());
        verify(repositorioSesion, never()).buscar(anyString());
    }

    @Test
    void noHaceNadaSiElRecorridoYaFinalizo() {
        RecorridoDTO finalizado = new RecorridoDTO(RECORRIDO_ID, "FINALIZADO", null, null);
        when(servicioNavegacion.consultar(RECORRIDO_ID)).thenReturn(finalizado);

        RecorridoDTO resultado = servicioCaptura.registrarRespuesta(RECORRIDO_ID, TipoInteraccion.SI_NO,
                List.of("SI"), PADRE);

        assertEquals(finalizado, resultado);
        verify(servicioNavegacion, never()).responder(anyString(), any(List.class));
        verify(repositorioObservaciones, never()).save(any());
    }

    @Test
    void siLaPreguntaEsperadaYaNoEsLaActualNoRegistraNiAvanzaNada() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n2", List.of("SI", "NO")));

        assertThrows(PreguntaDesactualizadaException.class, () -> servicioCaptura.registrarRespuesta(
                RECORRIDO_ID, TipoInteraccion.SI_NO, List.of("SI"), PADRE, "n1"));

        verify(servicioNavegacion, never()).responder(anyString(), any(List.class));
        verify(repositorioObservaciones, never()).save(any());
        verify(repositorioSesion, never()).buscar(anyString());
    }

    @Test
    void avanzaGuardaLaObservacionYElRecorridoEnUnaSolaTransaccion() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n1", List.of("SI", "NO")));
        RecorridoDTO esperado = recorridoConPregunta("n2", List.of("SI", "NO"));
        when(servicioNavegacion.responder(RECORRIDO_ID, List.of("SI"))).thenReturn(esperado);

        servicioCaptura.registrarRespuesta(RECORRIDO_ID, TipoInteraccion.SI_NO, List.of("SI"), PADRE);

        InOrder orden = inOrder(servicioNavegacion, repositorioObservaciones, servicioRecorridos, gestor);
        orden.verify(servicioNavegacion).responder(RECORRIDO_ID, List.of("SI"));
        orden.verify(repositorioObservaciones).save(any(ObservacionEntity.class));
        orden.verify(servicioRecorridos).actualizar(esperado);
        orden.verify(gestor).commit(any(TransactionStatus.class));
        verify(gestor, never()).rollback(any(TransactionStatus.class));
        verify(repositorioSesion, never()).guardar(any());
    }

    @Test
    void siFallaLaObservacionRevierteYDevuelveLaSesionAComoEstaba() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n1", List.of("SI", "NO")));
        when(servicioNavegacion.responder(RECORRIDO_ID, List.of("SI")))
                .thenReturn(recorridoConPregunta("n2", List.of("SI", "NO")));
        doThrow(new IllegalStateException("bd caída")).when(repositorioObservaciones)
                .save(any(ObservacionEntity.class));

        assertThrows(IllegalStateException.class, () -> servicioCaptura.registrarRespuesta(RECORRIDO_ID,
                TipoInteraccion.SI_NO, List.of("SI"), PADRE));

        verify(gestor).rollback(any(TransactionStatus.class));
        verify(gestor, never()).commit(any(TransactionStatus.class));
        verify(servicioRecorridos, never()).actualizar(any());
        verify(repositorioSesion).guardar(antes);
    }

    @Test
    void siElCandadoEstaTomadoNoTocaNada() {
        ServicioCapturaImpl ocupado = crear(new CandadoTomado());

        assertThrows(RecorridoOcupadoException.class, () -> ocupado.registrarRespuesta(RECORRIDO_ID,
                TipoInteraccion.SI_NO, List.of("SI"), PADRE));
        assertThrows(RecorridoOcupadoException.class, () -> ocupado.retroceder(RECORRIDO_ID));

        verifyNoInteractions(servicioNavegacion, repositorioObservaciones, repositorioSesion, gestor);
    }

    @Test
    void retrocederGuardaElRecorridoEnUnaSolaTransaccion() {
        RecorridoDTO anterior = recorridoConPregunta("n1", List.of("SI", "NO"));
        when(servicioNavegacion.retroceder(RECORRIDO_ID)).thenReturn(anterior);

        RecorridoDTO resultado = servicioCaptura.retroceder(RECORRIDO_ID);

        assertEquals(anterior, resultado);
        verify(servicioRecorridos).actualizar(anterior);
        verify(gestor).commit(any(TransactionStatus.class));
        verify(repositorioSesion, never()).guardar(any());
    }
}
