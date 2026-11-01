package org.signbb.backend.seguridad.servicios;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.seguridad.datos.entidades.LimiteIntentosEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioLimiteIntentos;
import org.signbb.backend.seguridad.modelo.TipoLimite;

/**
 * Tests de {@link LimitadorIntentos}: bloqueo por cuenta en PostgreSQL,
 * umbrales distintos para ingreso y registro, y clave hasheada.
 */
class LimitadorIntentosTest {

    private static final Duration BLOQUEO_LOGIN = Duration.ofMinutes(15);
    private static final Duration BLOQUEO_REGISTRO = Duration.ofHours(1);

    private RepositorioLimiteIntentos repositorio;
    private LimitadorIntentos limitador;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioLimiteIntentos.class);
        limitador = new LimitadorIntentos(repositorio, 5, BLOQUEO_LOGIN, 3, BLOQUEO_REGISTRO);
    }

    @Test
    void unBloqueoVigenteSeRechazaConElTiempoRestante() {
        Instant bloqueado = Instant.now().plusSeconds(600);
        LimiteIntentosEntity fila = fila(bloqueado);
        when(repositorio.findByTipoAndClave(eq(TipoLimite.LOGIN_CUENTA), anyString()))
                .thenReturn(Optional.of(fila));

        IntentosExcedidosException e = assertThrows(IntentosExcedidosException.class,
                () -> limitador.verificar(TipoLimite.LOGIN_CUENTA, "ana@correo.com"));

        assertTrue(e.getEspera().toSeconds() > 0 && e.getEspera().toSeconds() <= 600);
        assertEquals("Demasiados intentos fallidos. Vuelve a intentar en 10 minutos.", e.getMessage());
    }

    @Test
    void unBloqueoVencidoNoBloquea() {
        LimiteIntentosEntity fila = fila(Instant.now().minusSeconds(1));
        when(repositorio.findByTipoAndClave(eq(TipoLimite.LOGIN_CUENTA), anyString()))
                .thenReturn(Optional.of(fila));

        assertDoesNotThrow(() -> limitador.verificar(TipoLimite.LOGIN_CUENTA, "ana@correo.com"));
    }

    @Test
    void elIngresoUsaElMaximoYElBloqueoDeLogin() {
        limitador.registrarIntento(TipoLimite.LOGIN_CUENTA, "ana@correo.com");

        verify(repositorio).registrarIntento(eq("LOGIN_CUENTA"), anyString(),
                any(OffsetDateTime.class), any(OffsetDateTime.class), eq(5), any(OffsetDateTime.class));
    }

    @Test
    void limpiarBorraElContadorDeLaCuenta() {
        limitador.limpiar(TipoLimite.LOGIN_CUENTA, "ana@correo.com");

        verify(repositorio).borrar(eq(TipoLimite.LOGIN_CUENTA), anyString());
    }

    @Test
    void laClaveEsUnHashYNoElCorreo() {
        String clave = LimitadorIntentos.clave("  ANA@Correo.COM ");

        assertEquals(64, clave.length());
        assertNotEquals("ana@correo.com", clave);
    }

    private static LimiteIntentosEntity fila(Instant bloqueadoHasta) {
        LimiteIntentosEntity fila = new LimiteIntentosEntity();
        fila.setTipo(TipoLimite.LOGIN_CUENTA);
        fila.setClave(LimitadorIntentos.clave("ana@correo.com"));
        fila.setContador(5);
        fila.setVentanaInicio(Instant.now().minusSeconds(60));
        fila.setBloqueadoHasta(bloqueadoHasta);
        fila.setExpiraEn(bloqueadoHasta);
        fila.setActualizadoEn(Instant.now());
        return fila;
    }
}
