package org.signbb.backend.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.OpcionPendiente;
import org.signbb.backend.navegacion.modelo.PasoCausal;
import org.signbb.backend.navegacion.modelo.PasoRuta;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Integración de la cola de la selección múltiple con un Redis real (el
 * servicio {@code cache} de {@code contenedores/compose.yaml}) y un grafo
 * pequeño de dos ramas armado en código, de modo que la cola SÍ queda con una
 * recomendación pendiente (cosa que el grafo de ictericia no muestra).
 *
 * <pre>
 * inicio --inicia_navegacion--&gt; q1 (selección múltiple)
 *   q1 --A--&gt; qa (peso 3) --SI--&gt; ra (recomendación)
 *                          --NO--&gt; urg (urgencia)
 *   q1 --B--&gt; qb (peso 2) --SI--&gt; rb (recomendación)
 * </pre>
 *
 * <p>
 * Cada paso se guarda y se vuelve a leer con una instancia NUEVA de
 * {@link RepositorioSesionRedis}, como tras un reinicio del backend, y se
 * comprueba que la cola, la ruta, las recomendaciones y la foto para
 * {@code retroceder} sobreviven a la ida y vuelta por Redis. No necesita
 * Spring ni la base de datos. Si Redis no responde (sin contenedores) las
 * pruebas se saltan. Host y puerto se pueden cambiar con
 * {@code -Dsignbb.test.redis.host} y {@code -Dsignbb.test.redis.port}.
 */
class ColaSeleccionMultipleRedisIntegracionTest {

    private static LettuceConnectionFactory conexion;
    private static StringRedisTemplate redis;

    private GrafoClinico grafo;
    private BaseReglas base;
    private ContextoClinico contexto;
    private MotorNavegacion motor;
    private final Map<String, Nodo> nodos = new HashMap<>();
    private final List<String> sesionesCreadas = new ArrayList<>();

    @BeforeAll
    static void conectar() {
        String host = System.getProperty("signbb.test.redis.host", "localhost");
        int puerto = Integer.getInteger("signbb.test.redis.port", 6379);
        LettuceClientConfiguration configuracion = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        LettuceConnectionFactory fabrica = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, puerto), configuracion);
        fabrica.afterPropertiesSet();
        try {
            StringRedisTemplate plantilla = new StringRedisTemplate(fabrica);
            plantilla.opsForValue().get("signbb:prueba-conexion");
            conexion = fabrica;
            redis = plantilla;
        } catch (Exception sinRedis) {
            fabrica.destroy();
        }
        assumeTrue(redis != null,
                "requiere Redis en " + host + ":" + puerto + " (docker compose up -d en contenedores)");
    }

    @AfterAll
    static void desconectar() {
        if (conexion != null) {
            conexion.destroy();
        }
    }

    @BeforeEach
    void preparar() {
        grafo = new GrafoClinico();
        base = new BaseReglas();
        motor = new MotorNavegacion(new ServicioReglasImpl());
        contexto = mock(ContextoClinico.class);
        when(contexto.grafoDe(any())).thenReturn(grafo);
        when(contexto.baseDe(any())).thenReturn(base);

        agregar(new Nodo("inicio", "pregunta", "inicio", 0));
        agregar(new Nodo("q1", "pregunta", "q1", 1, false, List.of(), "SELECCION_MULTIPLE"));
        agregar(new Nodo("qa", "pregunta", "qa", 3));
        agregar(new Nodo("qb", "pregunta", "qb", 2));
        agregar(new Nodo("ra", "recomendacion", "ra", 1));
        agregar(new Nodo("rb", "recomendacion", "rb", 1));
        agregar(new Nodo("urg", "urgencia", "urg", 1));
        arista("inicio", "inicia_navegacion", "q1");
        arista("q1", "A", "qa");
        arista("q1", "B", "qb");
        arista("qa", "SI", "ra");
        arista("qa", "NO", "urg");
        arista("qb", "SI", "rb");
    }

    @AfterEach
    void limpiar() {
        for (String id : sesionesCreadas) {
            redis.delete(RepositorioSesionRedis.PREFIJO_SESION + id);
        }
    }

    private void agregar(Nodo nodo) {
        grafo.agregarNodo(nodo);
        nodos.put(nodo.getId(), nodo);
    }

    private void arista(String origen, String condicion, String destino) {
        grafo.conectarRespuestas(nodos.get(origen), nodos.get(destino), condicion);
    }

    /** Una instancia nueva por uso: simula el arranque limpio del backend. */
    private RepositorioSesionRedis repositorioNuevo() {
        return new RepositorioSesionRedis(redis, contexto);
    }

    /** Crea la sesión en Redis con la primera pregunta del recorrido. */
    private String iniciarSesion() {
        EstadoPaciente estado = new EstadoPaciente();
        ResultadoPaso inicial = motor.iniciar(estado, grafo);
        String id = repositorioNuevo().crear(estado, inicial).getId();
        sesionesCreadas.add(id);
        return id;
    }

    /** Carga la sesión, responde como lo hace el servicio y la vuelve a guardar. */
    private ResultadoPaso responder(String id, String... marcadas) {
        SesionTriaje sesion = repositorioNuevo().buscar(id);
        ResultadoPaso paso = motor.paso(grafo, base, sesion.getEstado(),
                sesion.getUltimoPaso().preguntaActual(), List.of(marcadas));
        sesion.setUltimoPaso(paso);
        repositorioNuevo().guardar(sesion);
        return paso;
    }

    private ResultadoPaso retroceder(String id) {
        SesionTriaje sesion = repositorioNuevo().buscar(id);
        ResultadoPaso paso = motor.retroceder(grafo, base, sesion.getEstado());
        sesion.setUltimoPaso(paso);
        repositorioNuevo().guardar(sesion);
        return paso;
    }

    private SesionTriaje recargar(String id) {
        return repositorioNuevo().buscar(id);
    }

    private static List<String> destinosEnCola(EstadoPaciente estado) {
        return estado.cola().stream().map(OpcionPendiente::idDestino).toList();
    }

    private static List<String> idsRuta(EstadoPaciente estado) {
        return estado.ruta().stream().map(PasoRuta::idNodo).toList();
    }

    private static List<String> ids(List<PasoCausal> camino) {
        return camino.stream().map(p -> p.nodo().getId()).toList();
    }

    @Test
    void laColaPendienteQuedaEnRedisYSobreviveAlReinicio() {
        String id = iniciarSesion();

        ResultadoPaso paso = responder(id, "A", "B");

        // Se sigue la rama de mayor peso (qa) y la otra queda pendiente.
        assertEquals("qa", paso.preguntaActual().getId());

        String json = redis.opsForValue().get(RepositorioSesionRedis.PREFIJO_SESION + id);
        assertNotNull(json, "la sesión debe estar en Redis");
        assertTrue(json.contains("\"navegacion\""), json);
        assertTrue(json.contains("\"idDestino\":\"qb\""), "la cola pendiente debe estar guardada: " + json);

        Long segundos = redis.getExpire(RepositorioSesionRedis.PREFIJO_SESION + id);
        assertNotNull(segundos);
        assertTrue(segundos > 0 && segundos <= Duration.ofHours(8).toSeconds(),
                "la sesión debe tener TTL de hasta 8 h, tiene " + segundos);

        SesionTriaje recargada = recargar(id);
        assertEquals("qa", recargada.getUltimoPaso().preguntaActual().getId());
        assertEquals(List.of("qb"), destinosEnCola(recargada.getEstado()));
        assertEquals(List.of("inicio", "q1", "qa"), idsRuta(recargada.getEstado()));
        assertEquals(List.of("A", "B"), recargada.getEstado().getRespuestas("q1"));
        assertFalse(recargada.getEstado().pasos().isEmpty());
    }

    @Test
    void trasLaPrimeraRecomendacionSigueLaOpcionPendienteYAlFinalSalenAmbas() {
        String id = iniciarSesion();
        responder(id, "A", "B");

        // qa -> ra: se guarda la recomendación y se retoma qb desde la cola.
        ResultadoPaso trasQa = responder(id, "SI");
        assertFalse(trasQa.finalizado());
        assertEquals("qb", trasQa.preguntaActual().getId());

        SesionTriaje enMedio = recargar(id);
        assertEquals("qb", enMedio.getUltimoPaso().preguntaActual().getId());
        assertEquals(List.of("ra"), List.copyOf(enMedio.getEstado().recomendaciones().keySet()));
        assertTrue(enMedio.getEstado().cola().isEmpty(), "la opción pendiente ya se tomó");
        assertEquals(List.of("inicio", "q1", "qb"), idsRuta(enMedio.getEstado()));

        // qb -> rb: cierra con las dos recomendaciones, cada una con su camino.
        ResultadoPaso fin = responder(id, "SI");
        assertTrue(fin.finalizado());
        assertEquals(NivelAlerta.NORMAL, fin.nivel());
        assertEquals(List.of("ra", "rb"),
                fin.recomendaciones().stream().map(r -> r.nodo().getId()).toList());

        ResultadoPaso guardado = recargar(id).getUltimoPaso();
        assertTrue(guardado.finalizado());
        assertEquals("rb", guardado.destino().getId());
        assertEquals(List.of("ra", "rb"),
                guardado.recomendaciones().stream().map(r -> r.nodo().getId()).toList());
        assertEquals(List.of("inicio", "q1", "qa", "ra"), ids(guardado.recomendaciones().get(0).camino()));
        assertEquals(List.of("inicio", "q1", "qb", "rb"), ids(guardado.recomendaciones().get(1).camino()));
    }

    @Test
    void unaAlertaEnUnaRamaCierraConLaAlertaSolaYVaciaLaColaGuardada() {
        String id = iniciarSesion();
        responder(id, "A", "B");

        ResultadoPaso fin = responder(id, "NO"); // qa -> urgencia

        assertTrue(fin.finalizado());
        assertEquals("urg", fin.destino().getId());
        assertTrue(fin.recomendaciones().isEmpty());

        SesionTriaje recargada = recargar(id);
        assertTrue(recargada.getUltimoPaso().finalizado());
        assertEquals("urg", recargada.getUltimoPaso().destino().getId());
        assertTrue(recargada.getUltimoPaso().recomendaciones().isEmpty());
        assertTrue(recargada.getEstado().cola().isEmpty(), "la alerta descarta lo pendiente");
    }
}
