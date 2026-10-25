package org.signbb.backend.mantenimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioLimiteIntentos;

/** Tests de {@link LimpiadorLimites}: borra lo vencido con un margen de un día. */
class LimpiadorLimitesTest {

    @Test
    void borraLoVencidoHaceMasDeUnDia() {
        RepositorioLimiteIntentos repositorio = mock(RepositorioLimiteIntentos.class);
        Instant ahora = Instant.parse("2026-10-24T12:00:00Z");
        when(repositorio.borrarVencidos(ahora.minus(LimpiadorLimites.MARGEN))).thenReturn(3);
        LimpiadorLimites limpiador = new LimpiadorLimites(repositorio);

        assertEquals(3, limpiador.limpiar(ahora));
        verify(repositorio).borrarVencidos(ahora.minus(LimpiadorLimites.MARGEN));
    }
}
