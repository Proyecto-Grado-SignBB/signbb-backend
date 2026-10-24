package org.signbb.backend.cache;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.signbb.backend.navegacion.modelo.CandadoRecorrido;
import org.signbb.backend.navegacion.modelo.RecorridoOcupadoException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Candado por recorrido en Redis ({@code signbb:candado:recorrido:<id>}).
 *
 * <p>
 * Se toma con {@code SET NX} y caduca solo a los 10 segundos, para que una
 * caída del servidor no deje el recorrido bloqueado. Cada toma guarda una
 * ficha única y solo quien la tiene puede soltarlo (así una operación lenta,
 * cuyo candado ya caducó, no suelta el de otra). Si Redis no responde, falla
 * con {@link CacheNoDisponibleException} (503), igual que las sesiones.
 */
@Component
public class CandadoRecorridoRedis implements CandadoRecorrido {

    /** Prefijo de las claves de candado en Redis. */
    public static final String PREFIJO = "signbb:candado:recorrido:";

    /** Tiempo máximo que un candado puede quedar tomado. */
    static final Duration DURACION = Duration.ofSeconds(10);

    private static final Logger LOG = LoggerFactory.getLogger(CandadoRecorridoRedis.class);

    /** Borra la clave solo si todavía guarda la ficha de quien la toma. */
    private static final RedisScript<Long> SOLTAR = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    /** Inyecta el template de Redis. */
    public CandadoRecorridoRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public <T> T conCandado(String recorridoId, Supplier<T> accion) {
        String clave = PREFIJO + recorridoId;
        String ficha = UUID.randomUUID().toString();
        if (!tomar(clave, ficha)) {
            throw new RecorridoOcupadoException();
        }
        try {
            return accion.get();
        } finally {
            soltar(clave, ficha);
        }
    }

    private boolean tomar(String clave, String ficha) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(clave, ficha, DURACION));
        } catch (DataAccessException e) {
            throw new CacheNoDisponibleException(
                    "el almacenamiento de sesiones no está disponible", e);
        }
    }

    /** Suelta el candado; si Redis falla, caduca solo y no se tapa el resultado de la operación. */
    private void soltar(String clave, String ficha) {
        try {
            redis.execute(SOLTAR, List.of(clave), ficha);
        } catch (DataAccessException e) {
            LOG.warn("No se pudo soltar el candado {}; caduca solo: {}", clave, e.toString());
        }
    }
}
