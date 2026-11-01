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
import org.signbb.backend.navegacion.modelo.Sintoma;
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
        EstadoPaciente estado = new EstadoPaciente();
        estado.registrarRespuesta("grafo_activo_color/q_neuro_gate", "Sí");
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_activo_color/q_neuro_areas").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        SesionTriaje recuperada = repositorio.buscar(sesion.getId());

        assertEquals(sesion.getId(), recuperada.getId());
        assertEquals(List.of("Sí"), recuperada.getEstado().getRespuestas("grafo_activo_color/q_neuro_gate"));
        assertEquals("grafo_activo_color/q_neuro_areas", recuperada.getUltimoPaso().preguntaActual().getId());
        assertFalse(recuperada.getUltimoPaso().finalizado());
    }

    @Test
    void guardarYBuscarSesionFinalizada() {
        EstadoPaciente estado = new EstadoPaciente();
        estado.registrarRespuesta("grafo_activo_color/q_neuro_gate", "Sí");
        estado.registrarRespuesta("grafo_activo_color/q_neuro_areas", "SOMNOLENCIA");
        Nodo urgente = grafo.obtenerNodoPorId("grafo_activo_color/T_URGENCIA").orElseThrow();

        SesionTriaje sesion = new SesionTriaje("s-finalizada", estado, ResultadoPaso.terminal(
                urgente, NivelAlerta.URGENCIA, List.of(base.obtenerRegla("grafo_activo_color/R_NEURO_URG")),
                grafo.obtenerCaminoCausal(urgente)));
        repositorio.guardar(sesion);

        SesionTriaje recuperada = repositorio.buscar("s-finalizada");

        assertTrue(recuperada.getUltimoPaso().finalizado());
        assertEquals(NivelAlerta.URGENCIA, recuperada.getUltimoPaso().nivel());
        assertEquals("grafo_activo_color/T_URGENCIA", recuperada.getUltimoPaso().destino().getId());
        assertEquals(1, recuperada.getUltimoPaso().razones().size());
        assertEquals("grafo_activo_color/R_NEURO_URG", recuperada.getUltimoPaso().razones().get(0).getIdRegla());
        assertEquals(sesion.getUltimoPaso().caminoCausal().size(),
                recuperada.getUltimoPaso().caminoCausal().size());
    }

    @Test
    void elOrdenDeLasRespuestasSeConserva() {
        // El motor reproduce este orden para volver a la pregunta anterior.
        EstadoPaciente estado = new EstadoPaciente();
        estado.registrarRespuesta("pregunta_inicial", "Actividad y color");
        estado.registrarRespuesta("puente_neuro", "Revisar actividad y color");
        estado.registrarRespuesta("grafo_activo_color/q_neuro_gate", "Sí");
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_activo_color/q_neuro_areas").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        EstadoPaciente recuperado = repositorio.buscar(sesion.getId()).getEstado();

        assertEquals(List.of("pregunta_inicial", "puente_neuro", "grafo_activo_color/q_neuro_gate"),
                recuperado.sintomas().stream().map(Sintoma::idNodo).toList());
    }

    @Test
    void laPilaDePasosSobreviveAGuardarYBuscar() {
        EstadoPaciente estado = new EstadoPaciente();
        estado.registrarRespuesta("grafo_activo_color/q_neuro_gate", "Sí");
        estado.marcarReglaDisparada("grafo_activo_color/R_NEURO_URG");
        estado.apilarPaso(new PasoRecorrido("grafo_activo_color/q_neuro_gate",
                List.of("Sí"), List.of(), List.of("grafo_activo_color/R_NEURO_URG")));
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_activo_color/q_neuro_areas").orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        List<PasoRecorrido> pasos = repositorio.buscar(sesion.getId()).getEstado().pasos();

        assertEquals(List.of(new PasoRecorrido("grafo_activo_color/q_neuro_gate",
                List.of("Sí"), List.of(), List.of("grafo_activo_color/R_NEURO_URG"))), pasos);
    }

    @Test
    void laColaLaRutaYLasRecomendacionesSobrevivenAGuardarYBuscar() {
        String q = "grafo_activo_color/q_neuro_gate";
        String signos = "grafo_activo_color/q_neuro_areas";
        EstadoPaciente estado = new EstadoPaciente();
        estado.registrarRespuestas(q, List.of("Sí"));
        FotoNavegacion antes = estado.fotografiarNavegacion();
        estado.reemplazarRuta(List.of(new PasoRuta("inicio", ""), new PasoRuta(q, "inicia_navegacion")));
        estado.encolarOpcion(q, "NO", signos, List.of(new PasoRuta("inicio", ""), new PasoRuta(signos, "NO")));
        estado.registrarRecomendacion("grafo_activo_color/T_NORMAL",
                List.of(new PasoRuta("inicio", "")));
        estado.apilarPaso(new PasoRecorrido(q, List.of("Sí"), List.of(), List.of(), antes));
        Nodo pregunta = grafo.obtenerNodoPorId(signos).orElseThrow();

        SesionTriaje sesion = repositorio.crear(estado, ResultadoPaso.pregunta(pregunta));
        EstadoPaciente recuperado = repositorio.buscar(sesion.getId()).getEstado();

        assertEquals(estado.fotografiarNavegacion(), recuperado.fotografiarNavegacion());
        assertEquals(List.of(new OpcionPendiente(q, "NO", signos, 0,
                List.of(new PasoRuta("inicio", ""), new PasoRuta(signos, "NO")))), recuperado.cola());
        assertEquals(antes, recuperado.pasos().get(0).navegacionPrevia());
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

        EstadoPaciente estado = new EstadoPaciente();
        Nodo pregunta = grafo.obtenerNodoPorId("grafo_activo_color/q_neuro_areas").orElseThrow();

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
}
