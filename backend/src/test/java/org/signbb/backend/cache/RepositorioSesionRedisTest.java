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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.PasoRecorrido;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
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
    private BaseReglas base;
    private RepositorioSesion repositorio;

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

        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        grafo = GrafoClinicoMapper.desde(contenido.grafo());
        base = BaseReglasMapper.desde(contenido.reglas(), grafo);

        ContextoClinico contexto = mock(ContextoClinico.class);
        when(contexto.getGrafo()).thenReturn(grafo);
        when(contexto.getBase()).thenReturn(base);

        repositorio = new RepositorioSesionRedis(redis, contexto);
    }

    @Test
    void crearYBuscarSesionEnCurso() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-1");
        estado.registrarRespuesta("grafo_piel_ictericia/pregunta_coloracion", "SI");
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_piel_ictericia/pregunta_signos").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        SesionTriaje recuperada = repositorio.buscar(sesion.getId());

        assertEquals(sesion.getId(), recuperada.getId());
        assertEquals("SI", recuperada.getEstado().getRespuesta("grafo_piel_ictericia/pregunta_coloracion").orElseThrow());
        assertEquals("grafo_piel_ictericia/pregunta_signos", recuperada.getUltimoPaso().preguntaActual().getId());
        assertFalse(recuperada.getUltimoPaso().finalizado());
    }

    @Test
    void guardarYBuscarSesionFinalizada() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-2");
        estado.registrarRespuesta("grafo_piel_ictericia/pregunta_coloracion", "SI");
        estado.registrarRespuesta("grafo_piel_ictericia/pregunta_signos", "SOMNOLENCIA");
        estado.sumarPuntaje(5);
        Nodo urgente = grafo.obtenerNodoPorId("grafo_piel_ictericia/urgencia_inmediata").orElseThrow();

        SesionTriaje sesion = new SesionTriaje("s-finalizada", estado, ResultadoPaso.terminal(
                urgente, NivelAlerta.URGENCIA, List.of(base.obtenerRegla("grafo_piel_ictericia/R1")),
                grafo.obtenerCaminoCausal(urgente)));
        repositorio.guardar(sesion);

        SesionTriaje recuperada = repositorio.buscar("s-finalizada");

        assertTrue(recuperada.getUltimoPaso().finalizado());
        assertEquals(NivelAlerta.URGENCIA, recuperada.getUltimoPaso().nivel());
        assertEquals("grafo_piel_ictericia/urgencia_inmediata", recuperada.getUltimoPaso().destino().getId());
        assertEquals(1, recuperada.getUltimoPaso().razones().size());
        assertEquals("grafo_piel_ictericia/R1", recuperada.getUltimoPaso().razones().get(0).getIdRegla());
        assertEquals(sesion.getUltimoPaso().caminoCausal().size(),
                recuperada.getUltimoPaso().caminoCausal().size());
        assertEquals(5, recuperada.getEstado().getPuntaje());
    }

    @Test
    void sesionCerradaPorLimiteReconstruyeElNodoDeSistema() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-3");
        estado.registrarRespuesta("pregunta_inicial", "piel");
        Nodo pendiente = grafo.obtenerNodoPorId("puente_piel").orElseThrow();

        SesionTriaje sesion = new SesionTriaje("s-limite", estado, ResultadoPaso.terminal(
                MotorNavegacion.LIMITE_PREGUNTAS, NivelAlerta.NORMAL, List.of(),
                grafo.obtenerCaminoCausal(pendiente)));
        repositorio.guardar(sesion);

        ResultadoPaso recuperado = repositorio.buscar("s-limite").getUltimoPaso();

        assertTrue(recuperado.finalizado());
        assertEquals(MotorNavegacion.LIMITE_PREGUNTAS, recuperado.destino());
        assertEquals(NivelAlerta.NORMAL, recuperado.nivel());
    }

    @Test
    void elOrdenDeLasRespuestasSeConserva() {
        // El motor reproduce este orden para volver a la pregunta anterior.
        EstadoPaciente estado = new EstadoPaciente("s-redis-4");
        estado.registrarRespuesta("pregunta_inicial", "piel");
        estado.registrarRespuesta("puente_piel", "coloracion_amarilla");
        estado.registrarRespuesta("grafo_piel_ictericia/pregunta_coloracion", "SI");
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_piel_ictericia/pregunta_signos").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        EstadoPaciente recuperado = repositorio.buscar(sesion.getId()).getEstado();

        assertEquals(List.of("pregunta_inicial", "puente_piel", "grafo_piel_ictericia/pregunta_coloracion"),
                List.copyOf(recuperado.respuestasEnOrden().keySet()));
        assertEquals("grafo_piel_ictericia/pregunta_coloracion",
                recuperado.ultimaPreguntaRespondida().orElseThrow());
    }

    @Test
    void laPilaDePasosSobreviveAGuardarYBuscar() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-5");
        estado.registrarRespuesta("grafo_piel_ictericia/pregunta_coloracion", "SI");
        estado.marcarReglaDisparada("grafo_piel_ictericia/R1");
        estado.apilarPaso(new PasoRecorrido("grafo_piel_ictericia/pregunta_coloracion",
                List.of("SI"), List.of(), List.of("grafo_piel_ictericia/R1")));
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_piel_ictericia/pregunta_signos").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        List<PasoRecorrido> pasos = repositorio.buscar(sesion.getId()).getEstado().pasos();

        assertEquals(List.of(new PasoRecorrido("grafo_piel_ictericia/pregunta_coloracion",
                List.of("SI"), List.of(), List.of("grafo_piel_ictericia/R1"))), pasos);
    }

    @Test
    void sesionGuardadaSinHistorialCargaConLaPilaVacia() {
        // Formato anterior a la pila: no trae el campo historial.
        almacen.put("signbb:sesion:s-vieja", """
                {"id":"s-vieja","respuestas":{"pregunta_inicial":["piel"]},"reglasDisparadas":[],
                 "puntaje":0,"ultimoPaso":{"estado":"EN_CURSO","preguntaId":"puente_piel",
                 "nivel":null,"destinoId":null,"razonesIds":[],"caminoCausal":[]}}
                """);

        EstadoPaciente estado = repositorio.buscar("s-vieja").getEstado();

        assertTrue(estado.pasos().isEmpty());
        assertEquals(List.of("piel"), estado.getRespuestas("pregunta_inicial"));
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
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_piel_ictericia/pregunta_signos").orElseThrow();

        assertThrows(CacheNoDisponibleException.class, () -> repositorio.buscar("x"));
        assertThrows(CacheNoDisponibleException.class,
                () -> repositorio.crear(estado, ResultadoPaso.pregunta(pregunta)));
    }
}
