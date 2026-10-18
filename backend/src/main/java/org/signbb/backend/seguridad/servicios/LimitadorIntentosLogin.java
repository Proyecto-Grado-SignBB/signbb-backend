package org.signbb.backend.seguridad.servicios;

import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Bloquea el ingreso de un correo tras varios intentos fallidos seguidos. Un
 * contador por correo en Redis ({@code signbb:auth:intentos:<correo>}) sube con
 * cada fallo y se borra con un ingreso correcto. Al llegar al máximo el correo
 * queda bloqueado el tiempo configurado, contado desde el último fallo.
 *
 * <p>
 * Cuenta también los correos que no existen, para que el bloqueo no revele
 * cuáles cuentas existen. Si Redis no responde, el ingreso sigue sin límite
 * (se registra un aviso): bloquear a todos por una caída de la caché sería
 * peor que no limitar por un rato.
 */
@Component
public class LimitadorIntentosLogin {

    /** Prefijo de las claves de intentos en Redis. */
    public static final String PREFIJO = "signbb:auth:intentos:";

    private static final Logger LOG = LoggerFactory.getLogger(LimitadorIntentosLogin.class);

    private final StringRedisTemplate redis;
    private final int maxIntentos;
    private final Duration bloqueo;

    /** Inyecta Redis y los límites ({@code signbb.seguridad.login.*}). */
    public LimitadorIntentosLogin(StringRedisTemplate redis,
            @Value("${signbb.seguridad.login.max-intentos:5}") int maxIntentos,
            @Value("${signbb.seguridad.login.bloqueo:15m}") Duration bloqueo) {
        this.redis = redis;
        this.maxIntentos = maxIntentos;
        this.bloqueo = bloqueo;
    }

    /**
     * Revisa que el correo no esté bloqueado.
     *
     * @throws IntentosExcedidosException si ya llegó al máximo de fallos
     */
    public void verificar(String correo) {
        String clave = clave(correo);
        if (clave == null) {
            return;
        }
        try {
            String valor = redis.opsForValue().get(clave);
            if (valor != null && Long.parseLong(valor) >= maxIntentos) {
                Long segundos = redis.getExpire(clave);
                Duration espera = segundos != null && segundos > 0 ? Duration.ofSeconds(segundos) : bloqueo;
                throw new IntentosExcedidosException(espera);
            }
        } catch (DataAccessException | NumberFormatException e) {
            LOG.warn("No se pudo revisar el límite de intentos de ingreso: {}", e.toString());
        }
    }

    /** Anota un intento fallido; al llegar al máximo renueva el bloqueo completo. */
    public void registrarFallo(String correo) {
        String clave = clave(correo);
        if (clave == null) {
            return;
        }
        try {
            Long intentos = redis.opsForValue().increment(clave);
            if (intentos != null && (intentos == 1 || intentos >= maxIntentos)) {
                redis.expire(clave, bloqueo);
            }
        } catch (DataAccessException e) {
            LOG.warn("No se pudo anotar el intento fallido de ingreso: {}", e.toString());
        }
    }

    /** Borra los intentos del correo tras un ingreso correcto. */
    public void limpiar(String correo) {
        String clave = clave(correo);
        if (clave == null) {
            return;
        }
        try {
            redis.delete(clave);
        } catch (DataAccessException e) {
            LOG.warn("No se pudo borrar el contador de intentos de ingreso: {}", e.toString());
        }
    }

    /** La clave del correo normalizado, o {@code null} si viene vacío. */
    private static String clave(String correo) {
        String normalizado = ServicioCuentasImpl.normalizarCorreo(correo);
        return normalizado == null ? null : PREFIJO + normalizado.toLowerCase(Locale.ROOT);
    }
}
