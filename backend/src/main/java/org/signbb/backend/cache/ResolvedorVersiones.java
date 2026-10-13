package org.signbb.backend.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Versiones del contenido clínico: cada publicación guarda una foto del grafo
 * compuesto y de la base de reglas compuesta, y los recorridos se atan a la que
 * tenían al empezar.
 *
 * <p>
 * La base de datos es la fuente de verdad y Redis la copia caliente, con una
 * clave por versión y por contenido (grafo y reglas). Como una versión nunca
 * cambia, su copia en Redis nunca hay que invalidarla; el TTL es solo higiene.
 * Si Redis no responde se lee de la base de datos.
 */
@Component
public class ResolvedorVersiones {

    private static final Logger log = LoggerFactory.getLogger(ResolvedorVersiones.class);

    /** Prefijo de la clave de cada versión (le sigue el id): {@code signbb:contenido:v3:version:7:grafo}. */
    public static final String PREFIJO_CLAVE = "signbb:contenido:v3:version:";

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final RepositorioVersionContenido versiones;
    private final ResolvedorContenidoClinico contenido;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Inyecta Redis, el repositorio de versiones y el resolvedor del contenido vigente. */
    public ResolvedorVersiones(StringRedisTemplate redis, RepositorioVersionContenido versiones,
            ResolvedorContenidoClinico contenido) {
        this.redis = redis;
        this.versiones = versiones;
        this.contenido = contenido;
    }

    /** Clave en Redis del grafo de una versión. */
    public static String clave(Long id) {
        return PREFIJO_CLAVE + id + ":grafo";
    }

    /** Clave en Redis de las reglas de una versión. */
    public static String claveReglas(Long id) {
        return PREFIJO_CLAVE + id + ":reglas";
    }

    /**
     * Id de la versión vigente (la más reciente). Si todavía no hay ninguna
     * (contenido sembrado o publicado antes de existir las versiones), la
     * primera es una foto del contenido vigente en ese momento.
     */
    public synchronized Long idActual() {
        Long id = versiones.idMasReciente();
        if (id == null) {
            id = registrar(contenido.documentoGrafo(), contenido.documentoReglas(), "Contenido inicial");
            log.info("Primera versión del contenido clínico creada (id {}).", id);
        } else if (documentoReglas(id) == null) {
            // Versión creada antes de versionar las reglas: se guarda una nueva
            // con el contenido vigente para que los recorridos nuevos tengan
            // reglas; las versiones antiguas quedan sin reglas.
            id = registrar(contenido.documentoGrafo(), contenido.documentoReglas(), "Reglas versionadas");
            log.info("Versión con reglas creada (id {}) a partir de una anterior sin reglas.", id);
        }
        return id;
    }

    /**
     * Guarda una versión nueva (grafo y reglas compuestos) y devuelve su id. Se
     * llama dentro de la transacción de la publicación, para que se guarden
     * juntas o ninguna.
     */
    public Long registrar(GrafoClinicoJson grafo, BaseReglasJson reglas, String origen) {
        return versiones.save(new VersionContenidoEntity(grafo, reglas, origen)).getId();
    }

    /**
     * Grafo compuesto de una versión: Redis, y si no está, la base de datos.
     *
     * @throws IllegalStateException si la versión no existe
     */
    public GrafoClinicoJson documentoGrafo(Long id) {
        String clave = clave(id);
        String cacheado = leer(clave);
        if (cacheado != null) {
            return mapper.readValue(cacheado, GrafoClinicoJson.class);
        }
        GrafoClinicoJson grafo = versiones.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "la versión " + id + " del contenido clínico no existe en la base de datos"))
                .getGrafo();
        escribir(clave, mapper.writeValueAsString(grafo));
        return grafo;
    }

    /**
     * Base de reglas compuesta de una versión, o {@code null} si la versión es
     * anterior a que las reglas se versionaran (el que llama usa entonces las
     * vigentes). Redis es la copia caliente; la base de datos, la fuente.
     *
     * @throws IllegalStateException si la versión no existe
     */
    public BaseReglasJson documentoReglas(Long id) {
        String clave = claveReglas(id);
        String cacheado = leer(clave);
        if (cacheado != null) {
            return mapper.readValue(cacheado, BaseReglasJson.class);
        }
        VersionContenidoEntity version = versiones.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "la versión " + id + " del contenido clínico no existe en la base de datos"));
        BaseReglasJson reglas = version.getReglas();
        if (reglas != null) {
            escribir(clave, mapper.writeValueAsString(reglas));
        }
        return reglas;
    }

    /**
     * Borra de Redis la copia de estas versiones (grafo y reglas). Se llama
     * cuando las versiones ya se borraron de la base de datos. Si Redis no
     * responde no pasa nada: las claves vencen solas por su TTL.
     */
    public void olvidar(Collection<Long> ids) {
        List<String> claves = ids.stream().flatMap(id -> Stream.of(clave(id), claveReglas(id))).toList();
        if (claves.isEmpty()) {
            return;
        }
        try {
            redis.delete(claves);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible para borrar la copia de {} versiones ({}): vencerán por su TTL.",
                    ids.size(), e.getClass().getSimpleName());
        }
    }

    private String leer(String clave) {
        try {
            return redis.opsForValue().get(clave);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible para leer '{}' ({}): se lee de la base de datos.", clave,
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private void escribir(String clave, String json) {
        try {
            redis.opsForValue().set(clave, json, TTL);
        } catch (DataAccessException e) {
            log.warn("Redis no disponible para escribir '{}' ({}).", clave, e.getClass().getSimpleName());
        }
    }
}
