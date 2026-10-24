package org.signbb.backend.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.signbb.backend.navegacion.modelo.RecorridoOcupadoException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** Tests unitarios del candado por recorrido en Redis (template simulado). */
class CandadoRecorridoRedisTest {

    private static final String CLAVE = CandadoRecorridoRedis.PREFIJO + "r1";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valores;
    private CandadoRecorridoRedis candado;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        redis = mock(StringRedisTemplate.class);
        valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
        candado = new CandadoRecorridoRedis(redis);
    }

    @Test
    void tomaElCandadoConTiempoLimiteEjecutaYLoSueltaConSuFicha() {
        when(valores.setIfAbsent(eq(CLAVE), anyString(), any(Duration.class))).thenReturn(true);

        String resultado = candado.conCandado("r1", () -> "listo");

        assertEquals("listo", resultado);
        ArgumentCaptor<String> ficha = ArgumentCaptor.forClass(String.class);
        verify(valores).setIfAbsent(eq(CLAVE), ficha.capture(), eq(Duration.ofSeconds(10)));
        verify(redis).execute(any(RedisScript.class), eq(List.of(CLAVE)), eq(ficha.getValue()));
    }

    @Test
    void siYaEstaTomadoNoEjecutaLaAccionNiSueltaElCandadoAjeno() {
        when(valores.setIfAbsent(eq(CLAVE), anyString(), any(Duration.class))).thenReturn(false);
        AtomicBoolean corrio = new AtomicBoolean(false);

        assertThrows(RecorridoOcupadoException.class, () -> candado.conCandado("r1", () -> {
            corrio.set(true);
            return "x";
        }));

        assertFalse(corrio.get());
        verify(redis, never()).execute(any(RedisScript.class), any(List.class), any());
    }

    @Test
    void sueltaElCandadoAunqueLaAccionFalle() {
        when(valores.setIfAbsent(eq(CLAVE), anyString(), any(Duration.class))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> candado.conCandado("r1", () -> {
            throw new IllegalStateException("falló");
        }));

        verify(redis).execute(any(RedisScript.class), eq(List.of(CLAVE)), anyString());
    }

    @Test
    void siRedisNoRespondeAlTomarloDa503() {
        when(valores.setIfAbsent(eq(CLAVE), anyString(), any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("caído"));
        AtomicBoolean corrio = new AtomicBoolean(false);

        assertThrows(CacheNoDisponibleException.class, () -> candado.conCandado("r1", () -> {
            corrio.set(true);
            return "x";
        }));

        assertFalse(corrio.get());
    }

    @Test
    void siRedisFallaAlSoltarNoTapaElResultado() {
        when(valores.setIfAbsent(eq(CLAVE), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new DataAccessResourceFailureException("caído"));

        assertTrue(candado.conCandado("r1", () -> true));
    }
}
