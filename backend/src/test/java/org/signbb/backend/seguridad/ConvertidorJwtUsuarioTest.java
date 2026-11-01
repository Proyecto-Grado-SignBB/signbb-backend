package org.signbb.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.ServicioCuentas;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Tests de {@link ConvertidorJwtUsuario}: el token solo sirve si la cuenta
 * está activa y no cerró sus sesiones después de emitirlo.
 */
class ConvertidorJwtUsuarioTest {

    private static final Instant EMITIDO = Instant.parse("2026-01-01T08:00:00Z");

    private final ServicioCuentas cuentas = mock(ServicioCuentas.class);
    private final ConvertidorJwtUsuario convertidor = new ConvertidorJwtUsuario(cuentas);

    @Test
    void conTokenVigenteElPrincipalEsElUsuarioActual() {
        UsuarioActual ana = new UsuarioActual(3L, "Ana", "ana@correo.com", Rol.PADRE, null);
        when(cuentas.usuarioActivo(3L, EMITIDO)).thenReturn(Optional.of(ana));

        Authentication auth = convertidor.convert(jwt("3"));

        assertEquals(ana, auth.getPrincipal());
        assertEquals("ROLE_PADRE", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void siLaCuentaCerroSesionesDespuesDeEmitirElTokenDa401() {
        when(cuentas.usuarioActivo(3L, EMITIDO)).thenReturn(Optional.empty());

        // El texto del 401 lo pone el punto de entrada de ConfiguracionSeguridad, no la excepción.
        assertThrows(InvalidBearerTokenException.class, () -> convertidor.convert(jwt("3")));
    }

    private static Jwt jwt(String sub) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(sub)
                .issuedAt(EMITIDO).expiresAt(EMITIDO.plusSeconds(3600)).build();
    }
}
