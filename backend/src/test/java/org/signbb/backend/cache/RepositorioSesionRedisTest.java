package org.signbb.backend.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Tests unitarios del almacén de sesiones en Redis (template simulado). */
class RepositorioSesionRedisTest {

    private final Map<String, String> almacen = new HashMap<>();

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valores;
    private GrafoClinico grafo;
    private RepositorioSesion repositorio;

    private static String leer(String ruta) {
        try (InputStream is = RepositorioSesionRedisTest.class.getResourceAsStream(ruta)) {
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
        when(valores.get(anyString()))
                .thenAnswer(inv -> almacen.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            almacen.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(valores).set(anyString(), anyString(), any(Duration.class));

        grafo = GrafoClinicoParser.parseJsonToGrafoClinico(leer("/static/ejemplo_grafo.json"));
        BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(
                leer("/static/ejemplo_reglas.json"), grafo);

        ContextoClinico contexto = mock(ContextoClinico.class);
        when(contexto.getGrafo()).thenReturn(grafo);
        when(contexto.getBase()).thenReturn(base);

        repositorio = new RepositorioSesionRedis(redis, contexto);
    }

    @Test
    void crearYBuscarSesionEnCurso() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-1");
        estado.registrarRespuesta("pregunta_coloracion", "SI");
        Nodo pregunta = grafo.obtenerNodoPorId("pregunta_tiempo").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        SesionTriaje recuperada = repositorio.buscar(sesion.getId());

        assertEquals(sesion.getId(), recuperada.getId());
        assertEquals("SI", recuperada.getEstado().getRespuesta("pregunta_coloracion").orElseThrow());
        assertEquals("pregunta_tiempo", recuperada.getUltimoPaso().preguntaActual().getId());
        assertFalse(recuperada.getUltimoPaso().finalizado());
    }

    @Test
    void guardarYBuscarSesionFinalizada() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-2");
        estado.registrarRespuesta("pregunta_coloracion", "SI");
        estado.registrarRespuesta("pregunta_tiempo", "MAS_DE_24_HORAS");
        estado.sumarPuntaje(5);
        Nodo urgente = grafo.obtenerNodoPorId("consulta_medica_urgente").orElseThrow();
        BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(
                leer("/static/ejemplo_reglas.json"), grafo);

        SesionTriaje sesion = new SesionTriaje("s-finalizada", estado, ResultadoPaso.terminal(
                urgente, NivelAlerta.URGENCIA, List.of(base.obtenerRegla("R1")),
                grafo.obtenerCaminoCausal(urgente)));
        repositorio.guardar(sesion);

        SesionTriaje recuperada = repositorio.buscar("s-finalizada");

        assertTrue(recuperada.getUltimoPaso().finalizado());
        assertEquals(NivelAlerta.URGENCIA, recuperada.getUltimoPaso().nivel());
        assertEquals("consulta_medica_urgente", recuperada.getUltimoPaso().destino().getId());
        assertEquals(1, recuperada.getUltimoPaso().razones().size());
        assertEquals("R1", recuperada.getUltimoPaso().razones().get(0).getIdRegla());
        assertEquals(sesion.getUltimoPaso().caminoCausal().size(),
                recuperada.getUltimoPaso().caminoCausal().size());
        assertEquals(5, recuperada.getEstado().getPuntaje());
    }

    @Test
    void sesionInexistenteDa404() {
        assertThrows(RecursoNoEncontradoException.class, () -> repositorio.buscar("no-existe"));
    }

    @Test
    void redisCaidoDaCacheNoDisponibleEnBuscarYGuardar() {
        when(valores.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("cache caído"));
        doThrow(new DataAccessResourceFailureException("cache caído"))
                .when(valores).set(anyString(), anyString(), any(Duration.class));

        EstadoPaciente estado = new EstadoPaciente("s-redis-3");
        Nodo pregunta = grafo.obtenerNodoPorId("pregunta_tiempo").orElseThrow();

        assertThrows(CacheNoDisponibleException.class, () -> repositorio.buscar("x"));
        assertThrows(CacheNoDisponibleException.class,
                () -> repositorio.crear(estado, ResultadoPaso.pregunta(pregunta)));
    }
}
