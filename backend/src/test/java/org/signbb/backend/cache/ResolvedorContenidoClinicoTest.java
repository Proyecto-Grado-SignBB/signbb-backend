package org.signbb.backend.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.ObjectMapper;

/** Tests unitarios de la resolución cache-aside del contenido clínico. */
class ResolvedorContenidoClinicoTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valores;
    private RepositorioGrafoClinico grafos;
    private RepositorioBaseReglas bases;
    private ResolvedorContenidoClinico resolvedor;

    private GrafoClinicoEntity filaGrafo;
    private BaseReglasEntity filaReglas;

    private static String leer(String ruta) {
        try (InputStream is = ResolvedorContenidoClinicoTest.class.getResourceAsStream(ruta)) {
            if (is == null) {
                throw new IllegalStateException("No se encontró " + ruta);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        redis = mock(StringRedisTemplate.class);
        valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
        grafos = mock(RepositorioGrafoClinico.class);
        bases = mock(RepositorioBaseReglas.class);

        filaGrafo = new GrafoClinicoEntity();
        filaGrafo.setEstructura(GrafoClinicoParser.parseJsonToDocumento(leer("/static/ejemplo_grafo.json")));
        filaReglas = new BaseReglasEntity();
        filaReglas.setEstructura(BaseReglasParser.parseJsonToDocumento(leer("/static/ejemplo_reglas.json")));

        resolvedor = new ResolvedorContenidoClinico(redis, grafos, bases);
    }

    @Test
    void hitDeRedisNoTocaLaBase() {
        String cacheado = new ObjectMapper().writeValueAsString(filaGrafo.getEstructura());
        when(valores.get(ResolvedorContenidoClinico.CLAVE_GRAFO)).thenReturn(cacheado);

        assertEquals(11, resolvedor.documentoGrafo().graph().nodes().size());
        verify(grafos, never()).findAll();
    }

    @Test
    void missLeeLaBaseYPueblaElCache() {
        when(valores.get(ResolvedorContenidoClinico.CLAVE_GRAFO)).thenReturn(null);
        when(grafos.findAll()).thenReturn(List.of(filaGrafo));

        assertEquals(11, resolvedor.documentoGrafo().graph().nodes().size());
        verify(valores).set(eq(ResolvedorContenidoClinico.CLAVE_GRAFO), anyString(),
                eq(Duration.ofHours(24)));
    }

    @Test
    void redisCaidoDegradaALaBaseDeDatos() {
        when(valores.get(ResolvedorContenidoClinico.CLAVE_GRAFO))
                .thenThrow(new DataAccessResourceFailureException("cache caído"));
        when(grafos.findAll()).thenReturn(List.of(filaGrafo));

        assertEquals(11, resolvedor.documentoGrafo().graph().nodes().size());
    }

    @Test
    void falloAlEscribirElCacheNoRompeLaResolucion() {
        when(valores.get(ResolvedorContenidoClinico.CLAVE_REGLAS)).thenReturn(null);
        when(bases.findAll()).thenReturn(List.of(filaReglas));
        doThrow(new DataAccessResourceFailureException("cache caído"))
                .when(valores).set(anyString(), anyString(), any(Duration.class));

        assertEquals(4, resolvedor.documentoReglas().rules().size());
    }

    @Test
    void baseSinContenidoFallaConErrorExplicito() {
        when(valores.get(ResolvedorContenidoClinico.CLAVE_REGLAS)).thenReturn(null);
        when(bases.findAll()).thenReturn(List.of());

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
