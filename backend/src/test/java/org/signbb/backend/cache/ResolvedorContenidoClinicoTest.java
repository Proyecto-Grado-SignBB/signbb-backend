package org.signbb.backend.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.ObjectMapper;

/**
 * Tests unitarios de la resolución cache-aside del contenido clínico: la base
 * guarda raíz, subgrafos y reglas por separado y el resolvedor cachea el
 * resultado ya compuesto.
 */
class ResolvedorContenidoClinicoTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valores;
    private RepositorioGrafoClinico grafos;
    private RepositorioBaseReglas bases;
    private ResolvedorContenidoClinico resolvedor;

    private GrafoClinicoEntity filaRaiz;
    private GrafoClinicoEntity filaSubgrafo;
    private BaseReglasEntity filaReglas;

    private static GrafoClinicoEntity filaGrafo(GrafoClinicoJson documento) {
        GrafoClinicoEntity fila = new GrafoClinicoEntity();
        fila.setEstructura(documento);
        return fila;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        redis = mock(StringRedisTemplate.class);
        valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
        grafos = mock(RepositorioGrafoClinico.class);
        bases = mock(RepositorioBaseReglas.class);

        filaRaiz = filaGrafo(ContenidoEjemplo.raiz());
        filaSubgrafo = filaGrafo(ContenidoEjemplo.subgrafos().get(0));
        filaReglas = new BaseReglasEntity();
        filaReglas.setEstructura(ContenidoEjemplo.bases().get(0));

        resolvedor = new ResolvedorContenidoClinico(redis, grafos, bases);
    }

    @Test
    void hitDeRedisNoTocaLaBase() {
        GrafoClinicoJson compuesto = ContenidoEjemplo.compuesto().grafo();
        when(valores.get(ResolvedorContenidoClinico.CLAVE_GRAFO))
                .thenReturn(new ObjectMapper().writeValueAsString(compuesto));

        assertEquals(7, resolvedor.documentoGrafo().graph().nodes().size());
        verify(grafos, never()).findAll();
    }

    @Test
    void missComponeDesdeLaBaseYPueblaAmbasClaves() {
        when(grafos.findAll()).thenReturn(List.of(filaRaiz, filaSubgrafo));
        when(bases.findAll()).thenReturn(List.of(filaReglas));

        GrafoClinicoJson documento = resolvedor.documentoGrafo();

        // Raíz sin referencias ni puentes vacíos (2) + subgrafo de ictericia (5).
        assertEquals(7, documento.graph().nodes().size());
        assertTrue(documento.graph().nodes().containsKey("grafo_piel_ictericia/pregunta_coloracion"));
        verify(valores).set(eq(ResolvedorContenidoClinico.CLAVE_GRAFO), anyString(),
                eq(Duration.ofHours(24)));
        verify(valores).set(eq(ResolvedorContenidoClinico.CLAVE_REGLAS), anyString(),
                eq(Duration.ofHours(24)));
    }

    @Test
    void redisCaidoDegradaALaBaseDeDatos() {
        when(valores.get(ResolvedorContenidoClinico.CLAVE_GRAFO))
                .thenThrow(new DataAccessResourceFailureException("cache caído"));
        when(grafos.findAll()).thenReturn(List.of(filaRaiz, filaSubgrafo));
        when(bases.findAll()).thenReturn(List.of(filaReglas));

        assertEquals(7, resolvedor.documentoGrafo().graph().nodes().size());
    }

    @Test
    void falloAlEscribirElCacheNoRompeLaResolucion() {
        when(grafos.findAll()).thenReturn(List.of(filaRaiz, filaSubgrafo));
        when(bases.count()).thenReturn(1L);
        when(bases.findAll()).thenReturn(List.of(filaReglas));
        doThrow(new DataAccessResourceFailureException("cache caído"))
                .when(valores).set(anyString(), anyString(), any(Duration.class));

        BaseReglasJson reglas = resolvedor.documentoReglas();

        assertEquals(3, reglas.rules().size());
        assertTrue(reglas.rules().containsKey("grafo_piel_ictericia/R1"));
    }

    @Test
    void filasDelFormatoPrevioSeIgnoran() {
        GrafoClinicoJson legado = new GrafoClinicoJson(new GrafoClinicoJson.Grafo(
                null, "legado", true, List.of(), null, "x", Map.of(), Map.of(), List.of()));
        BaseReglasEntity reglasLegado = new BaseReglasEntity();
        reglasLegado.setEstructura(new BaseReglasJson(null, Map.of()));
        when(grafos.findAll()).thenReturn(List.of(filaGrafo(legado), filaRaiz, filaSubgrafo));
        when(bases.findAll()).thenReturn(List.of(reglasLegado, filaReglas));

        assertEquals(7, resolvedor.documentoGrafo().graph().nodes().size());
    }

    @Test
    void sinGrafoRaizFallaConErrorExplicito() {
        when(grafos.findAll()).thenReturn(List.of(filaSubgrafo));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolvedor.documentoGrafo());
        assertTrue(error.getMessage().contains("grafo_raiz"));
    }

    @Test
    void baseSinContenidoFallaConErrorExplicito() {
        when(bases.count()).thenReturn(0L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolvedor.documentoReglas());
        assertEquals("no hay base de reglas en la base de datos; la siembra inicial "
                + "(DatabaseInit) debió poblarla", error.getMessage());
    }

    @Test
    void invalidarBorraLasClavesDeContenido() {
        resolvedor.invalidar();

        verify(redis).delete(List.of(
                ResolvedorContenidoClinico.CLAVE_GRAFO,
                ResolvedorContenidoClinico.CLAVE_REGLAS));
    }
}
