package org.signbb.backend.cache;

import java.time.Duration;
import java.util.List;

import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Resuelve el contenido clínico (grafo y base de reglas) con patrón
 * cache-aside: Redis como copia caliente y la base de datos relacional como
 * fuente de verdad, según el documento de diseño.
 *
 * <p>
 * El classpath NO participa del runtime: los JSON de ejemplo son solo la
 * semilla de {@code DatabaseInit}. Si Redis no responde se degrada a la base
 * de datos (Redis nunca es dependencia dura del arranque ni de la consulta);
 * si la base no tiene contenido, falla con un error explícito.
 */
@Component
public class ResolvedorContenidoClinico {

    private static final Logger log = LoggerFactory.getLogger(ResolvedorContenidoClinico.class);

    /** Clave del grafo clínico en Redis. */
    public static final String CLAVE_GRAFO = "signbb:contenido:grafo";

    /** Clave de la base de reglas en Redis. */
    public static final String CLAVE_REGLAS = "signbb:contenido:reglas";

    /**
     * TTL del contenido: higiene del cache; la invalidación real es
     * {@link #invalidar()}.
     */
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final RepositorioGrafoClinico grafos;
    private final RepositorioBaseReglas bases;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Inyecta el template de Redis y los repositorios de contenido clínico. */
    public ResolvedorContenidoClinico(StringRedisTemplate redis,
            RepositorioGrafoClinico grafos, RepositorioBaseReglas bases) {
        this.redis = redis;
        this.grafos = grafos;
        this.bases = bases;
    }

    /** Documento del grafo clínico: cache Redis → base de datos. */
    public GrafoClinicoJson documentoGrafo() {
        String cacheado = leerCache(CLAVE_GRAFO);
        if (cacheado != null) {
            return mapper.readValue(cacheado, GrafoClinicoJson.class);
        }
        GrafoClinicoJson documento = grafos.findAll().stream().findFirst()
                .map(GrafoClinicoEntity::getEstructura)
                .orElseThrow(() -> new IllegalStateException(
                        "no hay grafo clínico en la base de datos; la siembra inicial "
                                + "(DatabaseInit) debió poblarla"));
        escribirCache(CLAVE_GRAFO, mapper.writeValueAsString(documento));
        return documento;
    }

    /** Documento de la base de reglas: cache Redis → base de datos. */
    public BaseReglasJson documentoReglas() {
        String cacheado = leerCache(CLAVE_REGLAS);
        if (cacheado != null) {
            return mapper.readValue(cacheado, BaseReglasJson.class);
        }
        BaseReglasJson documento = bases.findAll().stream().findFirst()
                .map(BaseReglasEntity::getEstructura)
                .orElseThrow(() -> new IllegalStateException(
                        "no hay base de reglas en la base de datos; la siembra inicial "
                                + "(DatabaseInit) debió poblarla"));
        escribirCache(CLAVE_REGLAS, mapper.writeValueAsString(documento));
        return documento;
    }

    /**
     * Invalida la copia en caliente (borra las claves). Pensado para el
     * módulo de Autoría: cuando el contenido cambie en la base de datos, se
     * invoca y la siguiente resolución refresca el cache.
     * Esta eliminacion solo se sede hacer si no hay sesiones activas
     */
    public void invalidar() {
        try {
            redis.delete(List.of(CLAVE_GRAFO, CLAVE_REGLAS));
        } catch (DataAccessException e) {
            log.warn("No se pudo invalidar el cache de contenido ({}): se usará su TTL.",
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Lee el cache de forma tolerante a fallos: null si Redis está caído o vacío.
     */
    private String leerCache(String clave) {
        try {
            return redis.opsForValue().get(clave);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible para leer '{}' ({}): se resuelve desde la base de datos.",
                    clave, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Escribe el cache de forma tolerante: si falla solo se pierde la copia
     * caliente y la próxima resolución vuelve a la base de datos.
     */
    private void escribirCache(String clave, String json) {
        try {
            redis.opsForValue().set(clave, json, TTL);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible para escribir '{}' ({}): la próxima resolución "
                    + "usará la base de datos.", clave, e.getClass().getSimpleName());
        }
    }
}
