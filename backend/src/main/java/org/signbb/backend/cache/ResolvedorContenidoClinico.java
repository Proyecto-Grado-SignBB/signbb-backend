package org.signbb.backend.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
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
 *
 * <p>
 * La base de datos guarda cada grafo por separado (el raíz del sistema y un
 * subgrafo por médico) y cada base de reglas con su {@code grafoId}. Este
 * resolvedor los compone con {@link CompositorGrafoClinico} y cachea el
 * resultado plano, que es lo único que ven {@code ContextoClinico} y el motor.
 */
@Component
public class ResolvedorContenidoClinico {

    private static final Logger log = LoggerFactory.getLogger(ResolvedorContenidoClinico.class);

    /**
     * Clave del grafo clínico ya compuesto en Redis. Lleva {@code v2} para no
     * leer el documento único del formato previo que pudiera seguir cacheado.
     */
    public static final String CLAVE_GRAFO = "signbb:contenido:v2:grafo";

    /** Clave de la base de reglas ya compuesta en Redis. */
    public static final String CLAVE_REGLAS = "signbb:contenido:v2:reglas";

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

    /**
     * Grafo clínico compuesto (raíz + subgrafos, sin referencias): cache
     * Redis → base de datos.
     */
    public GrafoClinicoJson documentoGrafo() {
        String cacheado = leerCache(CLAVE_GRAFO);
        if (cacheado != null) {
            return mapper.readValue(cacheado, GrafoClinicoJson.class);
        }
        return componerYCachear().grafo();
    }

    /**
     * Base de reglas compuesta (todas las bases, ids prefijados): cache Redis
     * → base de datos. Si no hay ninguna base (el sistema admite cero), devuelve
     * una base vacía.
     */
    public BaseReglasJson documentoReglas() {
        String cacheado = leerCache(CLAVE_REGLAS);
        if (cacheado != null) {
            return mapper.readValue(cacheado, BaseReglasJson.class);
        }
        return componerYCachear().reglas();
    }

    /**
     * Lee de la base de datos el grafo raíz, los subgrafos y las bases de
     * reglas, los compone con {@link CompositorGrafoClinico} y cachea ambos
     * resultados. Las filas del formato previo (grafo sin {@code id}, base sin
     * {@code grafoId}) se ignoran con un aviso.
     */
    private CompositorGrafoClinico.Contenido componerYCachear() {
        GrafoClinicoJson raiz = null;
        List<GrafoClinicoJson> subgrafos = new ArrayList<>();
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            GrafoClinicoJson documento = fila.getEstructura();
            String id = documento == null || documento.graph() == null ? null : documento.graph().id();
            if (id == null || id.isBlank()) {
                log.warn("Se ignora el grafo de la fila {}: no declara 'id' (formato previo).",
                        fila.getId());
            } else if (CompositorGrafoClinico.ID_RAIZ.equals(id)) {
                raiz = documento;
            } else {
                subgrafos.add(documento);
            }
        }
        if (raiz == null) {
            throw new IllegalStateException(
                    "no hay grafo clínico raíz ('" + CompositorGrafoClinico.ID_RAIZ
                            + "') en la base de datos; la siembra inicial (DatabaseInit) debió poblarla");
        }

        List<BaseReglasJson> documentosReglas = new ArrayList<>();
        for (BaseReglasEntity fila : bases.findAll()) {
            BaseReglasJson documento = fila.getEstructura();
            if (documento == null || documento.grafoId() == null || documento.grafoId().isBlank()) {
                log.warn("Se ignora la base de reglas de la fila {}: no declara 'grafoId' "
                        + "(formato previo).", fila.getId());
            } else {
                documentosReglas.add(documento);
            }
        }

        CompositorGrafoClinico.Contenido contenido =
                CompositorGrafoClinico.componer(raiz, subgrafos, documentosReglas);
        escribirCache(CLAVE_GRAFO, mapper.writeValueAsString(contenido.grafo()));
        escribirCache(CLAVE_REGLAS, mapper.writeValueAsString(contenido.reglas()));
        return contenido;
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
