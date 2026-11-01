package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.cache.RepositorioSesionRedis;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests de integración del cache Redis (requiere el servicio `cache` de
 * contenedores/compose.yaml). Sin Redis disponible se saltan con una
 * suposición — sin cache el contenido resuelve directo desde la base, que es
 * el camino de degradación que también interesa cubrir.
 *
 * <p>
 * Corren contra la base de datos real del entorno, que puede tener más
 * subgrafos publicados que la siembra inicial. Por eso los conteos esperados
 * no están escritos a mano: se calculan componiendo las filas guardadas.
 */
@SpringBootTest
class CacheRedisIntegracionTest {

    @Autowired
    private ResolvedorContenidoClinico resolvedor;

    @Autowired
    private RepositorioGrafoClinico grafos;

    @Autowired
    private RepositorioBaseReglas bases;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ContextoClinico contexto;

    @Autowired
    private RepositorioSesion sesiones;

    private Boolean redisResponde;

    @AfterEach
    void limpiarCache() {
        try {
            redis.delete(List.of(
                    ResolvedorContenidoClinico.CLAVE_GRAFO,
                    ResolvedorContenidoClinico.CLAVE_REGLAS));
        } catch (DataAccessException ignorada) {
            // sin Redis: nada que limpiar
        }
    }

    /**
     * Contenido que debe resolverse: se compone directamente de las filas que
     * hay en la base de datos en este momento, sin pasar por el cache ni por el
     * contexto, así el valor esperado sigue lo que haya publicado.
     */
    private CompositorGrafoClinico.Contenido contenidoGuardado() {
        GrafoClinicoJson raiz = null;
        List<GrafoClinicoJson> subgrafos = new ArrayList<>();
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            GrafoClinicoJson documento = fila.getEstructura();
            String id = documento == null || documento.graph() == null ? null : documento.graph().id();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (CompositorGrafoClinico.ID_RAIZ.equals(id)) {
                raiz = documento;
            } else {
                subgrafos.add(documento);
            }
        }
        assertNotNull(raiz, "la base de datos no tiene el grafo raíz: falta la siembra inicial");
        List<BaseReglasJson> reglas = new ArrayList<>();
        for (BaseReglasEntity fila : bases.findAll()) {
            BaseReglasJson documento = fila.getEstructura();
            if (documento != null && documento.grafoId() != null && !documento.grafoId().isBlank()) {
                reglas.add(documento);
            }
        }
        return CompositorGrafoClinico.componer(raiz, subgrafos, reglas);
    }

    /** Nodos del grafo compuesto con lo que hay guardado ahora mismo. */
    private int nodosEsperados() {
        return GrafoClinicoMapper.desde(contenidoGuardado().grafo()).size();
    }

    /**
     * True si el despliegue usa el modo cache (repositorio de sesiones Redis)
     * y Redis responde. No basta con que Redis sea alcanzable: si el bean de
     * sesiones no es el de Redis, las pruebas de integración del cache no
     * aplican.
     */
    private boolean modoCacheActivo() {
        return sesiones instanceof RepositorioSesionRedis && redisResponde();
    }

    /** True si Redis responde; el resultado se memoiza para toda la clase. */
    private boolean redisResponde() {
        if (redisResponde == null) {
            try {
                redis.opsForValue().get("signbb:prueba-conexion");
                redisResponde = true;
            } catch (DataAccessException e) {
                redisResponde = false;
            }
        }
        return redisResponde;
    }

    @Test
    @Transactional
    void sirveDelCacheAunqueSeBorreLaFilaDeLaBase() {
        assumeTrue(modoCacheActivo(), "requiere el modo cache (perfil docker con contenedores arriba)");
        int esperados = nodosEsperados(); // antes de borrar las filas
        resolvedor.invalidar();
        resolvedor.documentoGrafo();

        grafos.deleteAll();

        GrafoClinicoJson documento = resolvedor.documentoGrafo();
        assertEquals(esperados, GrafoClinicoMapper.desde(documento).size());
    }

    @Test
    void laSesionSobreviveUnReinicioSimulado() {
        assumeTrue(modoCacheActivo(), "requiere el modo cache (perfil docker con contenedores arriba)");
        Nodo inicial = contexto.getGrafo().obtenerNodoInicial().orElseThrow();
        EstadoPaciente estado = new EstadoPaciente("cache-integracion");
        SesionTriaje sesion = sesiones.crear(estado, ResultadoPaso.pregunta(inicial));

        // "reinicio": una instancia nueva del repositorio, como tras un arranque limpio
        RepositorioSesionRedis trasReinicio = new RepositorioSesionRedis(redis, contexto);
        SesionTriaje recuperada = trasReinicio.buscar(sesion.getId());

        assertEquals(sesion.getId(), recuperada.getId());
        assertEquals(inicial.getId(), recuperada.getUltimoPaso().preguntaActual().getId());

        redis.delete(RepositorioSesionRedis.PREFIJO_SESION + sesion.getId());
    }
}
