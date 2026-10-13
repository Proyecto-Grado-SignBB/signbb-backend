package org.signbb.backend.mantenimiento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Tests de {@link LimpiezaProgramada}: primero versiones, luego imágenes, y un fallo no frena el otro paso. */
class LimpiezaProgramadaTest {

    private LimpiadorVersiones versiones;
    private LimpiadorImagenes imagenes;
    private LimpiezaProgramada limpieza;

    @BeforeEach
    void preparar() {
        versiones = mock(LimpiadorVersiones.class);
        imagenes = mock(LimpiadorImagenes.class);
        limpieza = new LimpiezaProgramada(versiones, imagenes);
    }

    @Test
    void primeroLasVersionesYDespuesLasImagenes() {
        limpieza.ejecutar();

        InOrder orden = inOrder(versiones, imagenes);
        orden.verify(versiones).limpiar(any(Instant.class));
        orden.verify(imagenes).limpiar(any(Instant.class));
    }

    @Test
    void siFallanLasVersionesIgualLimpiaLasImagenes() {
        when(versiones.limpiar(any(Instant.class))).thenThrow(new IllegalStateException("base caída"));

        limpieza.ejecutar();

        verify(imagenes).limpiar(any(Instant.class));
    }

    @Test
    void siFallanLasImagenesNoSePropagaElError() {
        when(imagenes.limpiar(any(Instant.class))).thenThrow(new IllegalStateException("silo caído"));

        limpieza.ejecutar();

        verify(versiones).limpiar(any(Instant.class));
    }
}
