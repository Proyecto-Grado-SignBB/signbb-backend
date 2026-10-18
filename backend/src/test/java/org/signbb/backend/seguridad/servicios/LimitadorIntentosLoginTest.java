package org.signbb.backend.seguridad.servicios;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Tests de {@link LimitadorIntentosLogin}: bloquea al llegar al máximo de
 * fallos, cuenta por correo normalizado y no bloquea si Redis no responde.
 */
class LimitadorIntentosLoginTest {

    private static final String CLAVE = "signbb:auth:intentos:ana@correo.com";
    private static final Duration BLOQUEO = Duration.ofMinutes(15);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valores;
    private LimitadorIntentosLogin limitador;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparar() {
        redis = mock(StringRedisTemplate.class);
        valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
        limitador = new LimitadorIntentosLogin(redis, 5, BLOQUEO);
    }

    @Test
    void sinFallosPrevios_noBloquea() {
        when(valores.get(CLAVE)).thenReturn(null);

        assertDoesNotThrow(() -> limitador.verificar("ana@correo.com"));
    }

    @Test
    void conMenosFallosQueElMaximo_noBloquea() {
        when(valores.get(CLAVE)).thenReturn("4");

        assertDoesNotThrow(() -> limitador.verificar("ana@correo.com"));
    }

    @Test
    void alLlegarAlMaximo_bloqueaConElTiempoQueFalta() {
        when(valores.get(CLAVE)).thenReturn("5");
        when(redis.getExpire(CLAVE)).thenReturn(600L);

        IntentosExcedidosException e = assertThrows(IntentosExcedidosException.class,
                () -> limitador.verificar("ana@correo.com"));

        assertEquals(Duration.ofSeconds(600), e.getEspera());
        assertEquals("Demasiados intentos fallidos. Vuelve a intentar en 10 minutos.", e.getMessage());
    }

    @Test
    void sinTiempoRestanteConocido_usaElBloqueoCompleto() {
        when(valores.get(CLAVE)).thenReturn("7");
        when(redis.getExpire(CLAVE)).thenReturn(-1L);

        IntentosExcedidosException e = assertThrows(IntentosExcedidosException.class,
                () -> limitador.verificar("ana@correo.com"));

        assertEquals(BLOQUEO, e.getEspera());
    }

    @Test
    void elMensajeRedondeaLosMinutosHaciaArribaYUsaSingular() {
        assertEquals("Demasiados intentos fallidos. Vuelve a intentar en 1 minuto.",
                new IntentosExcedidosException(Duration.ofSeconds(20)).getMessage());
        assertEquals("Demasiados intentos fallidos. Vuelve a intentar en 2 minutos.",
                new IntentosExcedidosException(Duration.ofSeconds(61)).getMessage());
    }

    @Test
    void elCorreoSeNormalizaAntesDeContar() {
        when(valores.get(CLAVE)).thenReturn("5");
        when(redis.getExpire(CLAVE)).thenReturn(60L);

        assertThrows(IntentosExcedidosException.class, () -> limitador.verificar("  ANA@Correo.COM "));
    }

    @Test
    void elPrimerFalloCreaElContadorConElTiempoDeBloqueo() {
        when(valores.increment(CLAVE)).thenReturn(1L);

        limitador.registrarFallo("ana@correo.com");

        verify(redis).expire(CLAVE, BLOQUEO);
    }

    @Test
    void losFallosIntermediosNoRenuevanElTiempo() {
        when(valores.increment(CLAVE)).thenReturn(3L);

        limitador.registrarFallo("ana@correo.com");

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void alLlegarAlMaximoElBloqueoSeRenuevaCompleto() {
        when(valores.increment(CLAVE)).thenReturn(5L);

        limitador.registrarFallo("ana@correo.com");

        verify(redis).expire(CLAVE, BLOQUEO);
    }

    @Test
    void unIngresoCorrectoBorraElContador() {
        limitador.limpiar("ana@correo.com");

        verify(redis).delete(CLAVE);
    }

    @Test
    void unCorreoVacioNoCuentaNiBloquea() {
        assertDoesNotThrow(() -> limitador.verificar(null));
        limitador.registrarFallo("  ");
        limitador.limpiar(null);

        verify(valores, never()).increment(anyString());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void siRedisFallaElIngresoSigueSinLimite() {
        when(valores.get(CLAVE)).thenThrow(new QueryTimeoutException("sin respuesta"));
        when(valores.increment(CLAVE)).thenThrow(new QueryTimeoutException("sin respuesta"));
        when(redis.delete(CLAVE)).thenThrow(new QueryTimeoutException("sin respuesta"));

        assertDoesNotThrow(() -> limitador.verificar("ana@correo.com"));
        assertDoesNotThrow(() -> limitador.registrarFallo("ana@correo.com"));
        assertDoesNotThrow(() -> limitador.limpiar("ana@correo.com"));
    }
}
