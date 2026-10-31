package org.signbb.backend.mantenimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.media.AlmacenImagenes;
import org.signbb.backend.media.AlmacenImagenes.Guardada;
import org.signbb.backend.media.AlmacenNoDisponibleException;
import org.signbb.backend.media.ReferenciasImagenes;

/**
 * Tests de {@link LimpiadorImagenes}: borra solo lo que no usa nadie, lo que
 * tiene más de 24 h y lo que tiene nombre de imagen del servidor; ante la duda
 * no borra.
 */
class LimpiadorImagenesTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T03:30:00Z");
    private static final Instant VIEJA = AHORA.minus(Duration.ofDays(3));

    private static final String SIN_USO = "00000000-0000-0000-0000-000000000001.png";
    private static final String EN_LIENZO = "00000000-0000-0000-0000-000000000002.jpg";
    private static final String EN_COPIA = "00000000-0000-0000-0000-000000000003.webp";
    private static final String EN_GRAFO = "00000000-0000-0000-0000-000000000004.gif";
    private static final String EN_VERSION = "00000000-0000-0000-0000-000000000005.png";

    private AlmacenImagenes almacen;
    private ReferenciasImagenes referencias;
    private LimpiadorImagenes limpiador;

    @BeforeEach
    void preparar() {
        almacen = mock(AlmacenImagenes.class);
        referencias = mock(ReferenciasImagenes.class);
        when(referencias.hayGrafosPublicados()).thenReturn(true);
        when(referencias.enUso()).thenReturn(Set.of());
        limpiador = new LimpiadorImagenes(almacen, referencias);
    }

    private void guardadas(Guardada... g) {
        when(almacen.listar()).thenReturn(List.of(g));
    }

    private static Guardada vieja(String nombre) {
        return new Guardada(nombre, VIEJA);
    }

    private void hayContenidoEnUso() {
        when(referencias.enUso()).thenReturn(Set.of(EN_LIENZO, EN_COPIA, EN_GRAFO, EN_VERSION));
    }

    @Test
    void borraLaQueNadieUsa() {
        guardadas(vieja(SIN_USO));

        List<String> borradas = limpiador.limpiar(AHORA);

        assertEquals(List.of(SIN_USO), borradas);
        verify(almacen).eliminar(SIN_USO);
    }

    @Test
    void conservaLasQueUsaUnLienzoUnaCopiaPublicadaUnGrafoOUnaVersion() {
        hayContenidoEnUso();
        guardadas(vieja(EN_LIENZO), vieja(EN_COPIA), vieja(EN_GRAFO), vieja(EN_VERSION), vieja(SIN_USO));

        List<String> borradas = limpiador.limpiar(AHORA);

        assertEquals(List.of(SIN_USO), borradas);
        verify(almacen, never()).eliminar(EN_LIENZO);
        verify(almacen, never()).eliminar(EN_COPIA);
        verify(almacen, never()).eliminar(EN_GRAFO);
        verify(almacen, never()).eliminar(EN_VERSION);
    }

    @Test
    void sinGrafosEnLaBaseNoBorraNadaPorqueNoSabeQueSeUsa() {
        when(referencias.hayGrafosPublicados()).thenReturn(false);
        guardadas(vieja(SIN_USO));

        assertTrue(limpiador.limpiar(AHORA).isEmpty());
        verify(almacen, never()).eliminar(anyString());
    }

    @Test
    void siFallaBorrarUnaSigueConLasDemas() {
        String otra = "00000000-0000-0000-0000-000000000006.png";
        guardadas(vieja(SIN_USO), vieja(otra));
        doThrow(new AlmacenNoDisponibleException("sin silo", new RuntimeException())).when(almacen)
                .eliminar(SIN_USO);

        List<String> borradas = limpiador.limpiar(AHORA);

        assertEquals(List.of(otra), borradas);
        verify(almacen).eliminar(otra);
    }

    @Test
    void siNoSePuedeCalcularLoEnUsoNoBorraNada() {
        guardadas(vieja(SIN_USO));
        when(referencias.enUso()).thenThrow(new IllegalStateException("base caída"));

        assertThrows(IllegalStateException.class, () -> limpiador.limpiar(AHORA));
        verify(almacen, never()).eliminar(any());
    }
}
