package org.signbb.backend.seguridad.servicios;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import org.signbb.backend.seguridad.datos.entidades.LimiteIntentosEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioLimiteIntentos;
import org.signbb.backend.seguridad.modelo.TipoLimite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite de intentos por correo/cuenta, persistido en PostgreSQL.
 *
 * <p>
 * El alcance actual no limita por IP ni de forma global: solo por cuenta. El
 * ingreso cuenta los fallos y borra el contador tras un ingreso correcto; el
 * registro cuenta todos los intentos para una misma cuenta. La clave que se
 * guarda es un hash del correo normalizado, no el correo en claro.
 *
 * <p>
 * Al vivir en PostgreSQL, una caída de Redis no desactiva el límite. Si la
 * base de datos no responde, el ingreso tampoco puede validar la cuenta.
 */
@Component
public class LimitadorIntentos {

    private final RepositorioLimiteIntentos repositorio;
    private final int maximoLogin;
    private final Duration bloqueoLogin;
    private final int maximoRegistro;
    private final Duration bloqueoRegistro;

    /** Inyecta el repositorio y los umbrales de ingreso y registro. */
    public LimitadorIntentos(RepositorioLimiteIntentos repositorio,
            @Value("${signbb.seguridad.login.max-intentos:5}") int maximoLogin,
            @Value("${signbb.seguridad.login.bloqueo:15m}") Duration bloqueoLogin,
            @Value("${signbb.seguridad.registro.max-intentos:3}") int maximoRegistro,
            @Value("${signbb.seguridad.registro.bloqueo:1h}") Duration bloqueoRegistro) {
        this.repositorio = repositorio;
        this.maximoLogin = maximoLogin;
        this.bloqueoLogin = bloqueoLogin;
        this.maximoRegistro = maximoRegistro;
        this.bloqueoRegistro = bloqueoRegistro;
    }

    /**
     * Revisa que la cuenta no esté bloqueada.
     *
     * @throws IntentosExcedidosException si el bloqueo sigue vigente
     */
    @Transactional(readOnly = true)
    public void verificar(TipoLimite tipo, String correo) {
        String clave = clave(correo);
        if (clave == null) {
            return;
        }
        Optional<LimiteIntentosEntity> fila = repositorio.findByTipoAndClave(tipo, clave);
        if (fila.isEmpty()) {
            return;
        }
        Instant ahora = Instant.now();
        LimiteIntentosEntity limite = fila.get();
        if (limite.getBloqueadoHasta() != null && limite.getBloqueadoHasta().isAfter(ahora)) {
            throw new IntentosExcedidosException(Duration.between(ahora, limite.getBloqueadoHasta()));
        }
        // Si la ventana ya expiró, el siguiente intento empieza un contador nuevo
        // en el upsert de registrarIntento.
    }

    /** Anota un intento y fija el bloqueo si se alcanzó el máximo. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIntento(TipoLimite tipo, String correo) {
        String clave = clave(correo);
        if (clave == null) {
            return;
        }
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Duration bloqueo = bloqueo(tipo);
        OffsetDateTime fin = ahora.plus(bloqueo);
        repositorio.registrarIntento(tipo.name(), clave, ahora, fin, maximo(tipo), fin);
    }

    /** Borra el contador de la cuenta, por ejemplo tras un ingreso correcto. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limpiar(TipoLimite tipo, String correo) {
        String clave = clave(correo);
        if (clave == null) {
            return;
        }
        repositorio.borrar(tipo, clave);
    }

    /** El máximo configurado para el tipo de límite. */
    private int maximo(TipoLimite tipo) {
        return switch (tipo) {
            case LOGIN_CUENTA -> maximoLogin;
            case REGISTRO_CUENTA -> maximoRegistro;
        };
    }

    /** El bloqueo configurado para el tipo de límite. */
    private Duration bloqueo(TipoLimite tipo) {
        return switch (tipo) {
            case LOGIN_CUENTA -> bloqueoLogin;
            case REGISTRO_CUENTA -> bloqueoRegistro;
        };
    }

    /**
     * Hash SHA-256 del correo normalizado, o {@code null} si viene vacío.
     * Se usa como clave para no guardar correos en claro.
     */
    static String clave(String correo) {
        String normalizado = ServicioCuentasImpl.normalizarCorreo(correo);
        if (normalizado == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
