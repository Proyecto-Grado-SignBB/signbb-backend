package org.signbb.backend.seguridad.servicios;

import java.time.Duration;
import java.time.Instant;

import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.modelo.Rol;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * <p>
 * Cuánto dura depende de la cuenta: médicos y supervisores, la duración
 * clínica (8 h por defecto); un padre, 24 h, o 30 días si ingresó desde un
 * dispositivo seguro.
 */
@Service
public class ServicioTokens {

    /** Emisor que va en el claim {@code iss}. */
    public static final String EMISOR = "signbb";

    private final JwtEncoder codificador;
    private final Duration duracionClinica;
    private final Duration duracionFamilia;
    private final Duration duracionFamiliaSegura;

    /** Inyecta el codificador y cuánto vale el token de cada tipo de cuenta. */
    @Autowired
    public ServicioTokens(JwtEncoder codificador,
            @Value("${signbb.seguridad.jwt.duracion:8h}") Duration duracionClinica,
            @Value("${signbb.seguridad.jwt.duracion-familia:24h}") Duration duracionFamilia,
            @Value("${signbb.seguridad.jwt.duracion-familia-segura:30d}") Duration duracionFamiliaSegura) {
        this.codificador = codificador;
        this.duracionClinica = duracionClinica;
        this.duracionFamilia = duracionFamilia;
        this.duracionFamiliaSegura = duracionFamiliaSegura;
    }

    /** Con la misma duración clínica dada y las de familia por defecto (24 h y 30 días). */
    public ServicioTokens(JwtEncoder codificador, Duration duracionClinica) {
        this(codificador, duracionClinica, Duration.ofHours(24), Duration.ofDays(30));
    }

    /** Emite un token para la cuenta, desde un dispositivo que no se indicó como seguro. */
    public TokenEmitido emitir(UsuarioEntity usuario) {
        return emitir(usuario, false);
    }

    /**
     * Emite un token para la cuenta. {@code dispositivoSeguro} solo cuenta para
     * los padres: los médicos y supervisores siempre usan la duración clínica.
     */
    public TokenEmitido emitir(UsuarioEntity usuario, boolean dispositivoSeguro) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(duracionDe(usuario.getRol(), dispositivoSeguro));
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

    /** Cuánto dura el token de ese rol. */
    private Duration duracionDe(Rol rol, boolean dispositivoSeguro) {
        if (rol != Rol.PADRE) {
            return duracionClinica;
        }
        return dispositivoSeguro ? duracionFamiliaSegura : duracionFamilia;
    }
}
