package org.signbb.backend.seguridad.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.signbb.backend.seguridad.ConfiguracionSeguridad;
import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.modelo.Rol;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Tests de {@link ServicioTokens} con el codificador y el decodificador reales
 * de {@link ConfiguracionSeguridad}: el token lleva sub, rol y nombre, y uno
 * vencido o firmado con otra clave no pasa.
 */
class ServicioTokensTest {

    private static final SecretKey CLAVE = ConfiguracionSeguridad.clave("clave-de-prueba-de-al-menos-32-bytes!!");
    private static final SecretKey OTRA = ConfiguracionSeguridad.clave("otra-clave-de-prueba-de-32-bytes-o-mas");

    private final JwtDecoder decodificador = ConfiguracionSeguridad.decodificador(CLAVE);

    @Test
    void elTokenLlevaSubRolYNombre() {
        ServicioTokens tokens = new ServicioTokens(ConfiguracionSeguridad.codificador(CLAVE), Duration.ofHours(8));

        TokenEmitido emitido = tokens.emitir(medico());
        Jwt jwt = decodificador.decode(emitido.token());

        assertEquals("42", jwt.getSubject());
        assertEquals("MEDICO", jwt.getClaimAsString("rol"));
        assertEquals("Dra. A", jwt.getClaimAsString("nombre"));
        assertEquals(ServicioTokens.EMISOR, jwt.getClaimAsString("iss"));
        assertTrue(emitido.expiraEn().isAfter(Instant.now().plus(Duration.ofHours(7))));
    }

    @Test
    void laDuracionDependeDelRolYDelDispositivoSeguro() {
        ServicioTokens tokens = new ServicioTokens(ConfiguracionSeguridad.codificador(CLAVE), Duration.ofHours(8),
                Duration.ofHours(24), Duration.ofDays(30));
        Instant ahora = Instant.now();

        Duration padre = Duration.between(ahora, tokens.emitir(usuario(Rol.PADRE), false).expiraEn());
        Duration padreSeguro = Duration.between(ahora, tokens.emitir(usuario(Rol.PADRE), true).expiraEn());
        Duration medicoSeguro = Duration.between(ahora, tokens.emitir(usuario(Rol.MEDICO), true).expiraEn());
        Duration supervisor = Duration.between(ahora, tokens.emitir(usuario(Rol.MEDICO_SUPERVISOR), false).expiraEn());

        assertNear(Duration.ofHours(24), padre);
        assertNear(Duration.ofDays(30), padreSeguro);
        assertNear(Duration.ofHours(8), medicoSeguro);
        assertNear(Duration.ofHours(8), supervisor);
    }

    @Test
    void unTokenVencidoSeRechaza() {
        // Vencido hace una hora, más que la tolerancia de reloj (60 s) del decodificador.
        Instant hace2h = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ServicioTokens.EMISOR)
                .subject("42")
                .issuedAt(hace2h)
                .expiresAt(hace2h.plus(Duration.ofHours(1)))
                .claim("rol", "MEDICO")
                .build();
        String token = ConfiguracionSeguridad.codificador(CLAVE)
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThrows(JwtException.class, () -> decodificador.decode(token));
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        ServicioTokens tokens = new ServicioTokens(ConfiguracionSeguridad.codificador(OTRA), Duration.ofHours(8));

        String token = tokens.emitir(medico()).token();

        assertThrows(JwtException.class, () -> decodificador.decode(token));
    }

    @Test
    void unSecretoCortoNoArranca() {
        assertThrows(IllegalStateException.class, () -> ConfiguracionSeguridad.clave("corto"));
    }

    /** Comprueba que la duración real esté a pocos segundos de la esperada. */
    private static void assertNear(Duration esperada, Duration real) {
        assertTrue(real.minus(esperada).abs().compareTo(Duration.ofSeconds(30)) < 0,
                "esperaba cerca de " + esperada + " y fue " + real);
    }

    private static UsuarioEntity usuario(Rol rol) {
        UsuarioEntity u = medico();
        u.setRol(rol);
        return u;
    }

    private static UsuarioEntity medico() {
        UsuarioEntity u = new UsuarioEntity();
        u.setId(42L);
        u.setNombre("Dra. A");
        u.setCorreo("a@clinica.co");
        u.setRol(Rol.MEDICO);
        return u;
    }
}
