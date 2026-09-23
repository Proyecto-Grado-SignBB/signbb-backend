package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.cache.RepositorioSesionRedis;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests de integración del cache Redis (perfil docker: requiere el servicio
 * `cache` de contenedores/compose.yaml). Sin Redis disponible se saltan con
 * una suposición — en el perfil dev/CI el contenido resuelve directo desde
 * la base, que es el camino de degradación que también interesa cubrir.
 */
@SpringBootTest
class CacheRedisIntegracionTest {

    @Autowired
    private ResolvedorContenidoClinico resolvedor;

    @Autowired
    private RepositorioGrafoClinico grafos;

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
            // sin Redis (perfil dev): nada que limpiar
        }
    }

    /**
     * True si el despliegue usa el modo cache (perfil docker: repositorio de
     * sesiones Redis) y Redis responde. No basta con que Redis sea alcanzable:
     * en una máquina con los contenedores arriba pero perfil {@code dev}, el
     * bean de sesiones es el de memoria y las pruebas de integración del cache
     * no aplican.
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
    void laPrimeraResolucionPueblaElCache() {
        assumeTrue(modoCacheActivo(), "requiere el modo cache (perfil docker con contenedores arriba)");
        resolvedor.invalidar();

        GrafoClinicoJson documento = resolvedor.documentoGrafo();

        assertEquals(12, GrafoClinicoMapper.desde(documento).size());
        assertTrue(Boolean.TRUE.equals(redis.hasKey(ResolvedorContenidoClinico.CLAVE_GRAFO)));
    }

    @Test
    @Transactional
    void sirveDelCacheAunqueSeBorreLaFilaDeLaBase() {
        assumeTrue(modoCacheActivo(), "requiere el modo cache (perfil docker con contenedores arriba)");
        resolvedor.invalidar();
        resolvedor.documentoGrafo();

        grafos.deleteAll();

        GrafoClinicoJson documento = resolvedor.documentoGrafo();
        assertEquals(12, GrafoClinicoMapper.desde(documento).size());
    }

    @Test
    void laSesionSobreviveUnReinicioSimulado() {
        assumeTrue(modoCacheActivo(), "requiere el modo cache (perfil docker con contenedores arriba)");
        EstadoPaciente estado = new EstadoPaciente("cache-integracion");
        estado.registrarRespuesta("pregunta_coloracion", "SI");
        Nodo pregunta = contexto.getGrafo().obtenerNodoPorId("pregunta_tiempo").orElseThrow();
        SesionTriaje sesion = sesiones.crear(estado, ResultadoPaso.pregunta(pregunta));

        // "reinicio": una instancia nueva del repositorio, como tras un arranque limpio
        RepositorioSesionRedis trasReinicio = new RepositorioSesionRedis(redis, contexto);
        SesionTriaje recuperada = trasReinicio.buscar(sesion.getId());

        assertEquals(sesion.getId(), recuperada.getId());
        assertEquals("SI", recuperada.getEstado().getRespuesta("pregunta_coloracion").orElseThrow());
        assertEquals("pregunta_tiempo", recuperada.getUltimoPaso().preguntaActual().getId());

        redis.delete(RepositorioSesionRedis.PREFIJO_SESION + sesion.getId());
    }

    @Test
    void elContextoSirveContenidoCompleto() {
        // No requiere Redis: en dev resuelve directo desde la base (H2), lo que
        // verifica también el camino de degradación del cache de punta a punta.
        assertEquals(12, contexto.getGrafo().size());
        assertEquals(7, contexto.getBase().size());
    }
}
