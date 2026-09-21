package org.signbb.backend.observaciones.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.signbb.backend.observaciones.controladores.PreguntaDTO;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.datos.entidades.ObservacionEntity;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioObservaciones;
import org.signbb.backend.observaciones.modelo.TipoInteraccion;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;

/**
 * Pruebas de {@link ServicioCapturaImpl} con {@link ServicioNavegacion} y
 * {@link RepositorioObservaciones} mockeados.
 *
 * <p>
 * Asume JUnit 5 + Mockito (mockito-junit-jupiter), lo estándar en un
 * proyecto Spring Boot — ajustar si el proyecto usa otra combinación.
 */
@ExtendWith(MockitoExtension.class)
class ServicioCapturaImplTest {

    @Mock
    private ServicioNavegacion servicioNavegacion;

    @Mock
    private RepositorioObservaciones repositorioObservaciones;

    private ServicioCapturaImpl servicioCaptura;

    private static final String RECORRIDO_ID = "r1";

    @BeforeEach
    void setUp() {
        servicioCaptura = new ServicioCapturaImpl(servicioNavegacion, repositorioObservaciones);
    }

    private RecorridoDTO recorridoConPregunta(String idNodo, List<String> opciones) {
        PreguntaDTO pregunta = new PreguntaDTO(idNodo, "¿texto?", opciones, false, List.of());
        return new RecorridoDTO(RECORRIDO_ID, "EN_CURSO", pregunta, null);
    }

    @Test
    void reenviaUnaSolaOpcionYAuditaLaObservacion() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n1", List.of("SI", "NO")));
        RecorridoDTO esperado = recorridoConPregunta("n2", List.of("SI", "NO"));
        when(servicioNavegacion.responder(RECORRIDO_ID, List.of("SI"))).thenReturn(esperado);

        RecorridoDTO resultado = servicioCaptura.registrarRespuesta(RECORRIDO_ID, TipoInteraccion.SI_NO,
                List.of("SI"));

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
                TipoInteraccion.SELECCION_MULTIPLE, List.of("A", "B"));

        assertEquals(esperado, resultado);
        // Captura nunca decide cuál gana: siempre reenvía la lista completa tal cual.
        verify(servicioNavegacion).responder(RECORRIDO_ID, List.of("A", "B"));
    }

    @Test
    void rechazaOpcionQueNoEstaEnLaPreguntaActual() {
        when(servicioNavegacion.consultar(RECORRIDO_ID))
                .thenReturn(recorridoConPregunta("n1", List.of("SI", "NO")));

        assertThrows(IllegalArgumentException.class, () -> servicioCaptura
                .registrarRespuesta(RECORRIDO_ID, TipoInteraccion.SI_NO, List.of("TAL_VEZ")));

        verify(servicioNavegacion, never()).responder(anyString(), any(List.class));
        verify(repositorioObservaciones, never()).save(any());
    }

    @Test
    void noHaceNadaSiElRecorridoYaFinalizo() {
        RecorridoDTO finalizado = new RecorridoDTO(RECORRIDO_ID, "FINALIZADO", null, null);
        when(servicioNavegacion.consultar(RECORRIDO_ID)).thenReturn(finalizado);

        RecorridoDTO resultado = servicioCaptura.registrarRespuesta(RECORRIDO_ID, TipoInteraccion.SI_NO,
                List.of("SI"));

        assertEquals(finalizado, resultado);
        verify(servicioNavegacion, never()).responder(anyString(), any(List.class));
        verify(repositorioObservaciones, never()).save(any());
    }
}