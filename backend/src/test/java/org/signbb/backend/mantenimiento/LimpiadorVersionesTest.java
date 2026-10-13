package org.signbb.backend.mantenimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido.Resumen;
import org.signbb.backend.navegacion.modelo.ContextoClinico;

/** Tests de {@link LimpiadorVersiones}: borra de la base, de Redis y de memoria, y solo lo que corresponde. */
class LimpiadorVersionesTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T03:30:00Z");

    private RepositorioVersionContenido repositorio;
    private ResolvedorVersiones resolvedor;
    private ContextoClinico contexto;
    private LimpiadorVersiones limpiador;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioVersionContenido.class);
        resolvedor = mock(ResolvedorVersiones.class);
        contexto = mock(ContextoClinico.class);
        limpiador = new LimpiadorVersiones(repositorio, resolvedor, contexto);
    }

    /** Versiones n..1 (la más reciente primero), cada una guardada un día antes que la siguiente. */
    private void hay(int n) {
        List<Resumen> resumenes = new ArrayList<>();
        for (int id = n; id >= 1; id--) {
            Resumen r = mock(Resumen.class);
            when(r.getId()).thenReturn((long) id);
            when(r.getCreadaEn()).thenReturn(AHORA.minus(Duration.ofDays(30)).minus(Duration.ofDays(n - id)));
            resumenes.add(r);
        }
        when(repositorio.resumenes()).thenReturn(resumenes);
    }

    @Test
    void borraLasViejasDeLaBaseDeRedisYDeMemoria() {
        hay(25);

        List<Long> borradas = limpiador.limpiar(AHORA);

        List<Long> esperadas = List.of(5L, 4L, 3L, 2L, 1L);
        assertEquals(esperadas, borradas);
        InOrder orden = inOrder(repositorio, resolvedor, contexto);
        orden.verify(repositorio).deleteAllByIdInBatch(esperadas);
        orden.verify(resolvedor).olvidar(esperadas);
        orden.verify(contexto).olvidar(esperadas);
    }

    @Test
    void conPocasVersionesNoTocaNada() {
        hay(20);

        assertTrue(limpiador.limpiar(AHORA).isEmpty());

        verify(repositorio, never()).deleteAllByIdInBatch(org.mockito.ArgumentMatchers.any());
        verify(resolvedor, never()).olvidar(org.mockito.ArgumentMatchers.any());
        verify(contexto, never()).olvidar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sinVersionesNoHaceNada() {
        when(repositorio.resumenes()).thenReturn(List.of());

        assertTrue(limpiador.limpiar(AHORA).isEmpty());
        verify(repositorio, never()).deleteAllByIdInBatch(org.mockito.ArgumentMatchers.any());
    }
}
