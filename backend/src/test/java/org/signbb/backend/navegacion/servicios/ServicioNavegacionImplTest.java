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
 * sobre el contenido compuesto y un almacén de sesiones en memoria de prueba:
 * abrir un recorrido, responder, consultar y volver atrás guardando siempre la
 * sesión, más los errores de un recorrido inexistente o una respuesta vacía.
 */
class ServicioNavegacionImplTest {

    private static final String URGENCIA = "grafo_respiracion/q_resp_urgencia";

    /** Respuesta que descarta los signos de alarma y avanza a la alerta media. */
    private static final String NINGUNO = "No, ninguno de los anteriores";

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

    /** Abre un recorrido y lo lleva hasta la pregunta de signos de alarma. */
    private String hastaUrgencia() {
        String id = servicio.iniciar().recorridoId();
        servicio.responder(id, "respiracion");
        servicio.responder(id, "iniciar");
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
        String id = hastaUrgencia();

        assertEquals(URGENCIA, servicio.consultar(id).pregunta().id());
        assertEquals("grafo_respiracion/q_resp_consulta", servicio.responder(id, NINGUNO).pregunta().id());
        assertEquals("grafo_respiracion/q_resp_consulta", servicio.consultar(id).pregunta().id());
    }

    @Test
    void seleccionMultipleConUnaAlertaCierraEnUrgencia() {
        String id = hastaUrgencia();

        RecorridoDTO urgencia = servicio.responder(id, List.of("Quejido al respirar", "Aleteo nasal"));

        assertEquals("FINALIZADO", urgencia.estado());
        assertEquals("URGENCIA", urgencia.resultado().nivel());
        assertEquals("FINALIZADO", servicio.consultar(id).estado());
    }

    @Test
    void elCaminoDelResultadoTraeElTextoDeCadaPreguntaYLasRazonesSonNombres() {
        String id = hastaUrgencia();
        String textoUrgencia = servicio.consultar(id).pregunta().texto();

        RecorridoDTO cierre = servicio.responder(id, "Quejido al respirar");

        List<PasoCausalDTO> camino = cierre.resultado().caminoCausal();
        PasoCausalDTO ultimo = camino.get(camino.size() - 1);
        PasoCausalDTO previo = camino.get(camino.size() - 2);
        // lo cierra una regla, no una arista: el terminal llega sin condición
        assertEquals("", ultimo.condicion());
        assertEquals(cierre.resultado().mensaje(), ultimo.contenido());
        assertEquals(URGENCIA, previo.nodoId());
        assertEquals(textoUrgencia, previo.contenido());
        assertEquals(List.of("Regla Respiración Urgencia"), cierre.resultado().razones());
    }

    @Test
    void unRecorridoFinalizadoNoAceptaMasRespuestas() {
        String id = hastaUrgencia();
        RecorridoDTO cierre = servicio.responder(id, "Quejido al respirar");

        RecorridoDTO otraVez = servicio.responder(id, NINGUNO);

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
    void unaListaDeRespuestasVaciaSeRechaza() {
        String id = servicio.iniciar().recorridoId();

        assertThrows(IllegalArgumentException.class, () -> servicio.responder(id, List.of()));
    }

    @Test
    void retrocederQuedaGuardadoYLaConsultaLoRefleja() {
        String id = hastaUrgencia();
        servicio.responder(id, NINGUNO);

        RecorridoDTO atras = servicio.retroceder(id);

        assertEquals("EN_CURSO", atras.estado());
        assertEquals(URGENCIA, atras.pregunta().id());
        assertEquals(URGENCIA, servicio.consultar(id).pregunta().id());
    }

    @Test
    void trasRetrocederSePuedeCambiarLaRespuesta() {
        String id = hastaUrgencia();
        assertEquals("EN_CURSO", servicio.responder(id, NINGUNO).estado());
        servicio.retroceder(id);

        RecorridoDTO conUrgencia = servicio.responder(id, "Quejido al respirar");

        assertEquals("FINALIZADO", conUrgencia.estado());
        assertEquals("URGENCIA", conUrgencia.resultado().nivel());
        assertEquals("grafo_respiracion/urgencia_1", conUrgencia.resultado().destinoId());
    }

    @Test
    void retrocederSinRespuestasSeRechaza() {
        String id = servicio.iniciar().recorridoId();

        assertThrows(IllegalArgumentException.class, () -> servicio.retroceder(id));
    }
}
