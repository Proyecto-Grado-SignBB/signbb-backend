package org.signbb.backend.navegacion.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.observaciones.controladores.PasoCausalDTO;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests del servicio de navegación (la fachada que usa el controlador REST)
 * sobre el contenido real del seed y un almacén de sesiones en memoria:
 * iniciar, responder, consultar y retroceder guardando siempre la sesión.
 */
class ServicioNavegacionImplTest {

    private static final String CATEGORIA = "Actividad y comportamiento";
    private static final String REVISAR = "Revisar actividad y comportamiento";
    private static final String SI = "Sí";
    private static final String MOVIMIENTO = "Movimiento y tono";
    private static final String LLANTO = "Llanto e irritabilidad";
    private static final String ALARMA = "Mucho más dormido / cuesta despertarlo";
    private static final String CONSULTA = "Más irritable/llora más, pero calma";
    private static final String NORMAL = "Sobresaltos mientras duerme y luego normal";

    private ContextoClinico contexto;
    private ServicioNavegacion servicio;

    /** Doble de prueba de {@link RepositorioSesion} que no necesita Redis. */
    private static final class RepositorioSesionEnMemoria implements RepositorioSesion {

        private final Map<String, SesionTriaje> sesiones = new ConcurrentHashMap<>();

        @Override
        public SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial, Long versionId) {
            String id = UUID.randomUUID().toString();
            SesionTriaje sesion = new SesionTriaje(id, estado, pasoInicial, versionId);
            sesiones.put(id, sesion);
            return sesion;
        }

        @Override
        public SesionTriaje buscar(String id) {
            SesionTriaje sesion = sesiones.get(id);
            if (sesion == null) {
                throw new RecursoNoEncontradoException("Recorrido no encontrado: " + id);
            }
            return sesion;
        }

        @Override
        public void guardar(SesionTriaje sesion) {
            sesiones.put(sesion.getId(), sesion);
        }

        @Override
        public boolean existe(String id) {
            return sesiones.containsKey(id);
        }

        @Override
        public void eliminar(String id) {
            sesiones.remove(id);
        }
    }

    @BeforeEach
    void preparar() {
        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
        BaseReglas base = BaseReglasMapper.desde(contenido.reglas(), grafo);
        contexto = mock(ContextoClinico.class);
        when(contexto.idVersionActual()).thenReturn(1L);
        when(contexto.getGrafo()).thenReturn(grafo);
        when(contexto.grafoDe(any())).thenReturn(grafo);
        when(contexto.baseDe(any())).thenReturn(base);

        servicio = new ServicioNavegacionImpl(contexto, new MotorNavegacion(new ServicioReglasImpl()),
                new RepositorioSesionEnMemoria());
    }

    /** Abre un recorrido y lo lleva hasta la pregunta de tono. */
    private String hastaTono() {
        String id = servicio.iniciar().recorridoId();
        servicio.responder(id, CATEGORIA);
        servicio.responder(id, REVISAR);
        servicio.responder(id, SI);
        servicio.responder(id, MOVIMIENTO);
        return id;
    }

    /** Abre un recorrido y lo lleva hasta la pregunta de llanto. */
    private String hastaLlanto() {
        String id = servicio.iniciar().recorridoId();
        servicio.responder(id, CATEGORIA);
        servicio.responder(id, REVISAR);
        servicio.responder(id, SI);
        servicio.responder(id, LLANTO);
        return id;
    }

    @Test
    void iniciarAbreUnRecorridoEnCursoConLaPreguntaInicial() {
        RecorridoDTO inicio = servicio.iniciar();

        assertEquals("EN_CURSO", inicio.estado());
        assertEquals("pregunta_inicial", inicio.pregunta().id());
        assertNull(inicio.resultado());
    }

    @Test
    void conSubgrafosPublicadosLaConsultaEstaDisponible() {
        assertTrue(servicio.consultaDisponible());
    }

    @Test
    void sinSubgrafosLaConsultaNoEstaDisponibleYNoSePuedeIniciar() {
        GrafoClinico soloRaiz = GrafoClinicoMapper.desde(CompositorGrafoClinico
                .componer(ContenidoEjemplo.raiz(), List.of(), List.of()).grafo());
        when(contexto.getGrafo()).thenReturn(soloRaiz);
        when(contexto.grafoDe(any())).thenReturn(soloRaiz);

        assertFalse(servicio.consultaDisponible());
        assertThrows(ConsultaNoDisponibleException.class, () -> servicio.iniciar());
    }

    @Test
    void cadaRespuestaQuedaGuardadaYLaConsultaLaRefleja() {
        String id = hastaLlanto();

        assertEquals("grafo_actividad/q_act_llanto", servicio.consultar(id).pregunta().id());
        assertEquals("grafo_actividad/T_CONSULTA", servicio.responder(id, CONSULTA).resultado().destinoId());
        assertEquals("FINALIZADO", servicio.consultar(id).estado());
        assertEquals("CONSULTAR_MEDICO", servicio.consultar(id).resultado().nivel());
    }

    @Test
    void seleccionMultipleConUnaAlertaCierraEnUrgencia() {
        String id = hastaTono();

        RecorridoDTO urgencia = servicio.responder(id, List.of(ALARMA, "Más flojito de lo habitual"));

        assertEquals("FINALIZADO", urgencia.estado());
        assertEquals("URGENCIA", urgencia.resultado().nivel());
        assertEquals("FINALIZADO", servicio.consultar(id).estado());
    }

    @Test
    void elCaminoDelResultadoTraeElTextoDeCadaPreguntaYLasRazonesSonNombres() {
        String id = hastaTono();
        String textoTono = servicio.consultar(id).pregunta().texto();

        RecorridoDTO cierre = servicio.responder(id, ALARMA);

        List<PasoCausalDTO> camino = cierre.resultado().caminoCausal();
        PasoCausalDTO ultimo = camino.get(camino.size() - 1);
        PasoCausalDTO previo = camino.get(camino.size() - 2);
        assertEquals("", ultimo.condicion());
        assertEquals(cierre.resultado().mensaje(), ultimo.contenido());
        assertEquals("grafo_actividad/q_act_tono", previo.nodoId());
        assertEquals(textoTono, previo.contenido());
        assertEquals(List.of("Urgencia Actividad y Comportamiento"), cierre.resultado().razones());
    }

    @Test
    void unRecorridoFinalizadoNoAceptaMasRespuestas() {
        String id = hastaTono();
        RecorridoDTO cierre = servicio.responder(id, ALARMA);

        RecorridoDTO otraVez = servicio.responder(id, NORMAL);

        assertEquals("FINALIZADO", otraVez.estado());
        assertEquals(cierre.resultado().destinoId(), otraVez.resultado().destinoId());
    }

    @Test
    void unRecorridoInexistenteDa404() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar("no-existe"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.responder("no-existe", "SI"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.retroceder("no-existe"));
    }

    @Test
    void retrocederQuedaGuardadoYLaConsultaLoRefleja() {
        String id = hastaTono();
        servicio.responder(id, NORMAL);

        RecorridoDTO atras = servicio.retroceder(id);

        assertEquals("EN_CURSO", atras.estado());
        assertEquals("grafo_actividad/q_act_tono", atras.pregunta().id());
        assertEquals("grafo_actividad/q_act_tono", servicio.consultar(id).pregunta().id());
    }

    @Test
    void trasRetrocederSePuedeCambiarLaRespuesta() {
        String id = hastaTono();
        assertEquals("FINALIZADO", servicio.responder(id, NORMAL).estado());
        servicio.retroceder(id);

        RecorridoDTO conAlarma = servicio.responder(id, ALARMA);

        assertEquals("FINALIZADO", conAlarma.estado());
        assertEquals("URGENCIA", conAlarma.resultado().nivel());
        assertEquals("grafo_actividad/T_URGENCIA", conAlarma.resultado().destinoId());
    }
}
