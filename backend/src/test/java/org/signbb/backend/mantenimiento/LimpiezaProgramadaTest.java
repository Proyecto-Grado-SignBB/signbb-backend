package org.signbb.backend.mantenimiento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests de {@link LimpiezaProgramada}: versiones, imágenes y límites; un fallo no frena los demás. */
class LimpiezaProgramadaTest {

    private LimpiadorVersiones versiones;
    private LimpiadorImagenes imagenes;
    private LimpiadorLimites limites;
    private LimpiezaProgramada limpieza;

    @BeforeEach
    void preparar() {
        versiones = mock(LimpiadorVersiones.class);
        imagenes = mock(LimpiadorImagenes.class);
        limites = mock(LimpiadorLimites.class);
        limpieza = new LimpiezaProgramada(versiones, imagenes, limites);
    }

    @Test
    void siFallanLasVersionesIgualLimpiaLasDemas() {
        when(versiones.limpiar(any(Instant.class))).thenThrow(new IllegalStateException("base caída"));

        limpieza.ejecutar();

        verify(imagenes).limpiar(any(Instant.class));
        verify(limites).limpiar(any(Instant.class));
    }
}
