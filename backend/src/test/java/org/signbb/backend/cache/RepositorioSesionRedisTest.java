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
import static org.mockito.Mockito.verify;
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
import org.signbb.backend.navegacion.modelo.FotoNavegacion;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.OpcionPendiente;
import org.signbb.backend.navegacion.modelo.PasoRecorrido;
import org.signbb.backend.navegacion.modelo.PasoRuta;
import org.signbb.backend.navegacion.modelo.Recomendacion;
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
        when(contexto.grafoDe(any())).thenReturn(grafo);
        when(contexto.baseDe(any())).thenReturn(base);

        repositorio = new RepositorioSesionRedis(redis, contexto);
    }

    @Test
    void crearYBuscarSesionEnCurso() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-1");
        estado.registrarRespuesta("grafo_respiracion/q_resp_urgencia", "SI");
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_respiracion/q_resp_consulta").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        SesionTriaje recuperada = repositorio.buscar(sesion.getId());

        assertEquals(sesion.getId(), recuperada.getId());
        assertEquals("SI", recuperada.getEstado().getRespuesta("grafo_respiracion/q_resp_urgencia").orElseThrow());
        assertEquals("grafo_respiracion/q_resp_consulta", recuperada.getUltimoPaso().preguntaActual().getId());
        assertFalse(recuperada.getUltimoPaso().finalizado());
    }

    @Test
    void guardarYBuscarSesionFinalizada() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-2");
        estado.registrarRespuesta("grafo_respiracion/q_resp_urgencia", "SI");
        estado.registrarRespuesta("grafo_respiracion/q_resp_consulta", "SOMNOLENCIA");
        estado.sumarPuntaje(5);
        Nodo urgente = grafo.obtenerNodoPorId("grafo_respiracion/urgencia_1").orElseThrow();

        SesionTriaje sesion = new SesionTriaje("s-finalizada", estado, ResultadoPaso.terminal(
                urgente, NivelAlerta.URGENCIA, List.of(base.obtenerRegla("grafo_respiracion/R_RESP_URG")),
                grafo.obtenerCaminoCausal(urgente)));
        repositorio.guardar(sesion);

        SesionTriaje recuperada = repositorio.buscar("s-finalizada");

        assertTrue(recuperada.getUltimoPaso().finalizado());
        assertEquals(NivelAlerta.URGENCIA, recuperada.getUltimoPaso().nivel());
        assertEquals("grafo_respiracion/urgencia_1", recuperada.getUltimoPaso().destino().getId());
        assertEquals(1, recuperada.getUltimoPaso().razones().size());
        assertEquals("grafo_respiracion/R_RESP_URG", recuperada.getUltimoPaso().razones().get(0).getIdRegla());
        assertEquals(sesion.getUltimoPaso().caminoCausal().size(),
                recuperada.getUltimoPaso().caminoCausal().size());
        assertEquals(5, recuperada.getEstado().getPuntaje());
    }

    @Test
    void sesionCerradaPorLimiteReconstruyeElNodoDeSistema() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-3");
        estado.registrarRespuesta("pregunta_inicial", "respiracion");
        Nodo pendiente = grafo.obtenerNodoPorId("puente_respiracion").orElseThrow();

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
        estado.registrarRespuesta("pregunta_inicial", "respiracion");
        estado.registrarRespuesta("puente_respiracion", "iniciar");
        estado.registrarRespuesta("grafo_respiracion/q_resp_urgencia", "SI");
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_respiracion/q_resp_consulta").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        EstadoPaciente recuperado = repositorio.buscar(sesion.getId()).getEstado();

        assertEquals(List.of("pregunta_inicial", "puente_respiracion", "grafo_respiracion/q_resp_urgencia"),
                List.copyOf(recuperado.respuestasEnOrden().keySet()));
        assertEquals("grafo_respiracion/q_resp_urgencia",
                recuperado.ultimaPreguntaRespondida().orElseThrow());
    }

    @Test
    void laPilaDePasosSobreviveAGuardarYBuscar() {
        EstadoPaciente estado = new EstadoPaciente("s-redis-5");
        estado.registrarRespuesta("grafo_respiracion/q_resp_urgencia", "SI");
        estado.marcarReglaDisparada("grafo_respiracion/R_RESP_URG");
        estado.apilarPaso(new PasoRecorrido("grafo_respiracion/q_resp_urgencia",
                List.of("SI"), List.of(), List.of("grafo_respiracion/R_RESP_URG")));
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_respiracion/q_resp_consulta").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        List<PasoRecorrido> pasos = repositorio.buscar(sesion.getId()).getEstado().pasos();

        assertEquals(List.of(new PasoRecorrido("grafo_respiracion/q_resp_urgencia",
                List.of("SI"), List.of(), List.of("grafo_respiracion/R_RESP_URG"))), pasos);
    }

    @Test
    void sesionGuardadaSinHistorialCargaConLaPilaVacia() {
        // Formato anterior a la pila: no trae el campo historial.
        almacen.put("signbb:sesion:s-vieja", """
                {"id":"s-vieja","respuestas":{"pregunta_inicial":["respiracion"]},"reglasDisparadas":[],
                 "puntaje":0,"ultimoPaso":{"estado":"EN_CURSO","preguntaId":"puente_respiracion",
                 "nivel":null,"destinoId":null,"razonesIds":[],"caminoCausal":[]}}
                """);

        EstadoPaciente estado = repositorio.buscar("s-vieja").getEstado();

        assertTrue(estado.pasos().isEmpty());
        assertEquals(List.of("respiracion"), estado.getRespuestas("pregunta_inicial"));
    }

    @Test
    void laColaLaRutaYLasRecomendacionesSobrevivenAGuardarYBuscar() {
        String q = "grafo_respiracion/q_resp_urgencia";
        String signos = "grafo_respiracion/q_resp_consulta";
        EstadoPaciente estado = new EstadoPaciente("s-redis-cola");
        estado.registrarRespuestas(q, List.of("SI"));
        FotoNavegacion antes = estado.fotografiarNavegacion();
        estado.reemplazarRuta(List.of(new PasoRuta("inicio", ""), new PasoRuta(q, "inicia_navegacion")));
        estado.encolarOpcion(q, "NO", signos, List.of(new PasoRuta("inicio", ""), new PasoRuta(signos, "NO")));
        estado.registrarRecomendacion("grafo_respiracion/normal_1",
                List.of(new PasoRuta("inicio", "")));
        estado.apilarPaso(new PasoRecorrido(q, List.of("SI"), List.of(), List.of(), antes));
        Nodo pregunta = grafo.obtenerNodoPorId(signos).orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        EstadoPaciente recuperado = repositorio.buscar(sesion.getId()).getEstado();

        assertEquals(estado.fotografiarNavegacion(), recuperado.fotografiarNavegacion());
        assertEquals(List.of(new OpcionPendiente(q, "NO", signos, 0,
                List.of(new PasoRuta("inicio", ""), new PasoRuta(signos, "NO")))), recuperado.cola());
        assertEquals(antes, recuperado.pasos().get(0).navegacionPrevia());
    }

    @Test
    void elCierreSinAlertaConservaTodasLasRecomendaciones() {
        Nodo lactancia = grafo.obtenerNodoPorId("grafo_respiracion/normal_1").orElseThrow();
        Nodo inicial = grafo.obtenerNodoInicial().orElseThrow();
        EstadoPaciente estado = new EstadoPaciente("s-redis-reco");
        List<Recomendacion> alcanzadas = List.of(
                new Recomendacion(lactancia, grafo.obtenerCaminoCausal(lactancia)));

        SesionTriaje sesion = new SesionTriaje("s-reco", estado, ResultadoPaso.terminal(
                lactancia, NivelAlerta.NORMAL, List.of(), grafo.obtenerCaminoCausal(lactancia), alcanzadas));
        repositorio.guardar(sesion);
        ResultadoPaso recuperado = repositorio.buscar("s-reco").getUltimoPaso();

        assertTrue(recuperado.finalizado());
        assertEquals(1, recuperado.recomendaciones().size());
        assertEquals(lactancia.getId(), recuperado.recomendaciones().get(0).nodo().getId());
        assertEquals(alcanzadas.get(0).camino().size(), recuperado.recomendaciones().get(0).camino().size());
        assertEquals(inicial.getId(), recuperado.recomendaciones().get(0).camino().get(0).nodo().getId());
    }

    @Test
    void sesionGuardadaSinNavegacionCargaConLaColaVacia() {
        // Formato anterior a la cola de pendientes: sin "navegacion" ni "recomendaciones".
        almacen.put("signbb:sesion:s-sin-cola", """
                {"id":"s-sin-cola","respuestas":{"pregunta_inicial":["respiracion"]},"reglasDisparadas":[],
                 "puntaje":0,"ultimoPaso":{"estado":"EN_CURSO","preguntaId":"puente_respiracion",
                 "nivel":null,"destinoId":null,"razonesIds":[],"caminoCausal":[]},
                 "historial":[{"idPregunta":"pregunta_inicial","respuestas":["respiracion"],
                 "respuestasPrevias":[],"reglasMarcadas":[]}]}
                """);

        EstadoPaciente estado = repositorio.buscar("s-sin-cola").getEstado();

        assertTrue(estado.cola().isEmpty());
        assertTrue(estado.ruta().isEmpty());
        assertTrue(estado.recomendaciones().isEmpty());
        assertEquals(1, estado.pasos().size());
        assertEquals(null, estado.pasos().get(0).navegacionPrevia());
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
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_respiracion/q_resp_consulta").orElseThrow();

        assertThrows(CacheNoDisponibleException.class, () -> repositorio.buscar("x"));
        assertThrows(CacheNoDisponibleException.class,
                () -> repositorio.crear(estado, ResultadoPaso.pregunta(pregunta)));
    }

    @Test
    void existeYEliminarUsanLaClaveDeLaSesion() {
        when(redis.hasKey(RepositorioSesionRedis.PREFIJO_SESION + "s-1")).thenReturn(true);

        assertTrue(repositorio.existe("s-1"));
        assertFalse(repositorio.existe("otra"));
        repositorio.eliminar("s-1");

        verify(redis).delete(RepositorioSesionRedis.PREFIJO_SESION + "s-1");
    }

    @Test
    void redisCaidoDaCacheNoDisponibleEnExisteYEliminar() {
        when(redis.hasKey(anyString())).thenThrow(new DataAccessResourceFailureException("cache caído"));
        when(redis.delete(anyString())).thenThrow(new DataAccessResourceFailureException("cache caído"));

        assertThrows(CacheNoDisponibleException.class, () -> repositorio.existe("x"));
        assertThrows(CacheNoDisponibleException.class, () -> repositorio.eliminar("x"));
    }
}
