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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.ObjectMapper;

/** Tests de {@link ResolvedorVersiones}: la versión vigente, la foto de cada versión y el cache por versión. */
class ResolvedorVersionesTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valores;
    private RepositorioVersionContenido repositorio;
    private ResolvedorContenidoClinico contenido;
    private ResolvedorVersiones resolvedor;

    private final GrafoClinicoJson grafo = ContenidoEjemplo.compuesto().grafo();
    private final BaseReglasJson reglas = ContenidoEjemplo.compuesto().reglas();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        redis = mock(StringRedisTemplate.class);
        valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
        repositorio = mock(RepositorioVersionContenido.class);
        contenido = mock(ResolvedorContenidoClinico.class);
        resolvedor = new ResolvedorVersiones(redis, repositorio, contenido);
    }

    private VersionContenidoEntity fila(GrafoClinicoJson g) {
        return new VersionContenidoEntity(g, reglas, "prueba");
    }

    @Test
    void laVersionVigenteEsLaMasReciente() {
        when(repositorio.idMasReciente()).thenReturn(7L);
        when(repositorio.findById(7L)).thenReturn(Optional.of(fila(grafo)));

        assertEquals(7L, resolvedor.idActual());
        verify(repositorio, never()).save(any());
    }

    @Test
    void idActualConUnaVersionSinReglasCreaUnaConReglas() {
        when(repositorio.idMasReciente()).thenReturn(7L);
        when(repositorio.findById(7L))
                .thenReturn(Optional.of(new VersionContenidoEntity(grafo, null, "previa")));
        when(contenido.documentoGrafo()).thenReturn(grafo);
        when(contenido.documentoReglas()).thenReturn(reglas);
        VersionContenidoEntity nueva = mock(VersionContenidoEntity.class);
        when(nueva.getId()).thenReturn(8L);
        when(repositorio.save(any(VersionContenidoEntity.class))).thenReturn(nueva);

        assertEquals(8L, resolvedor.idActual());

        ArgumentCaptor<VersionContenidoEntity> captor = ArgumentCaptor.forClass(VersionContenidoEntity.class);
        verify(repositorio).save(captor.capture());
        assertEquals("Reglas versionadas", captor.getValue().getOrigen());
        assertEquals(reglas.rules().size(), captor.getValue().getReglas().rules().size());
    }

    @Test
    void sinVersionesLaPrimeraEsUnaFotoDelContenidoVigente() throws Exception {
        when(repositorio.idMasReciente()).thenReturn(null);
        when(contenido.documentoGrafo()).thenReturn(grafo);
        when(contenido.documentoReglas()).thenReturn(reglas);
        VersionContenidoEntity guardada = mock(VersionContenidoEntity.class);
        when(guardada.getId()).thenReturn(1L);
        when(repositorio.save(any(VersionContenidoEntity.class))).thenReturn(guardada);

        assertEquals(1L, resolvedor.idActual());

        ArgumentCaptor<VersionContenidoEntity> captor = ArgumentCaptor.forClass(VersionContenidoEntity.class);
        verify(repositorio).save(captor.capture());
        assertEquals(grafo.graph().nodes().size(), captor.getValue().getGrafo().graph().nodes().size());
        assertEquals(reglas.rules().size(), captor.getValue().getReglas().rules().size());
        assertEquals("Contenido inicial", captor.getValue().getOrigen());
    }

    @Test
    void registrarGuardaLaVersionYDevuelveSuId() {
        VersionContenidoEntity guardada = mock(VersionContenidoEntity.class);
        when(guardada.getId()).thenReturn(5L);
        when(repositorio.save(any(VersionContenidoEntity.class))).thenReturn(guardada);

        assertEquals(5L, resolvedor.registrar(grafo, reglas, "Publicación de 'grafo_x'"));

        ArgumentCaptor<VersionContenidoEntity> captor = ArgumentCaptor.forClass(VersionContenidoEntity.class);
        verify(repositorio).save(captor.capture());
        assertEquals("Publicación de 'grafo_x'", captor.getValue().getOrigen());
        assertEquals(reglas.rules().size(), captor.getValue().getReglas().rules().size());
        assertTrue(captor.getValue().getCreadaEn() != null);
    }

    @Test
    void lasReglasDeUnaVersionSeCacheanYSeLeenDeLaBase() {
        when(repositorio.findById(4L)).thenReturn(Optional.of(fila(grafo)));

        assertEquals(reglas.rules().size(), resolvedor.documentoReglas(4L).rules().size());

        verify(valores).set(eq("signbb:contenido:v3:version:4:reglas"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void unaVersionSinReglasDevuelveNullParaUsarLasVigentes() {
        VersionContenidoEntity sinReglas = new VersionContenidoEntity(grafo, null, "previa");
        when(repositorio.findById(8L)).thenReturn(Optional.of(sinReglas));

        assertEquals(null, resolvedor.documentoReglas(8L));
    }

    @Test
    void hitDeRedisNoTocaLaBase() throws Exception {
        when(valores.get(ResolvedorVersiones.clave(3L))).thenReturn(new ObjectMapper().writeValueAsString(grafo));

        assertEquals(grafo.graph().nodes().size(), resolvedor.documentoGrafo(3L).graph().nodes().size());
        verify(repositorio, never()).findById(any());
    }

    @Test
    void missLeeLaBaseYGuardaUnaClavePorVersion() {
        when(repositorio.findById(4L)).thenReturn(Optional.of(fila(grafo)));

        assertEquals(grafo.graph().nodes().size(), resolvedor.documentoGrafo(4L).graph().nodes().size());

        verify(valores).set(eq("signbb:contenido:v3:version:4:grafo"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void siRedisNoRespondeSeLeeDeLaBase() {
        when(valores.get(anyString())).thenThrow(new QueryTimeoutException("sin redis"));
        doThrow(new QueryTimeoutException("sin redis")).when(valores).set(anyString(), anyString(),
                any(Duration.class));
        when(repositorio.findById(2L)).thenReturn(Optional.of(fila(grafo)));

        assertEquals(grafo.graph().nodes().size(), resolvedor.documentoGrafo(2L).graph().nodes().size());
    }

    @Test
    void unaVersionQueNoExisteFallaConUnMensajeClaro() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> resolvedor.documentoGrafo(99L));
        assertTrue(e.getMessage().contains("99"));
    }
}
