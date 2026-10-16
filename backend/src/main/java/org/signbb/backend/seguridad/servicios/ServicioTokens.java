package org.signbb.backend.seguridad.servicios;

import java.time.Duration;
import java.time.Instant;

import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emite el JWT de una sesión, firmado con HS256. Lleva {@code sub} (id de la
 * cuenta), {@code rol} y {@code nombre}; lo valida el resource server de
 * Spring Security con la misma clave.
 */
@Service
public class ServicioTokens {

    /** Emisor que va en el claim {@code iss}. */
    public static final String EMISOR = "signbb";

    private final JwtEncoder codificador;
    private final Duration duracion;

    /** Inyecta el codificador y cuánto vale cada token. */
    public ServicioTokens(JwtEncoder codificador,
            @Value("${signbb.seguridad.jwt.duracion:8h}") Duration duracion) {
        this.codificador = codificador;
        this.duracion = duracion;
    }

    /** Emite un token para la cuenta. */
    public TokenEmitido emitir(UsuarioEntity usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(duracion);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("rol", usuario.getRol().name())
                .claim("nombre", usuario.getNombre())
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(token, expira);
    }
}
