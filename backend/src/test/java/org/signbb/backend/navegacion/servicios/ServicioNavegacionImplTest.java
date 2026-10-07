package org.signbb.backend.navegacion.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests del servicio de navegación sobre el contenido compuesto y un almacén
 * de sesiones en memoria de prueba: comprueban que volver atrás deja guardado
 * el estado recalculado, de modo que la consulta y la siguiente respuesta lo
 * ven.
 */
class ServicioNavegacionImplTest {

    private static final String URGENCIA = "grafo_respiracion/q_resp_urgencia";

    private ServicioNavegacion servicio;

    /** Doble de prueba de {@link RepositorioSesion} que no necesita Redis. */
    private static final class RepositorioSesionEnMemoria implements RepositorioSesion {

        private final Map<String, SesionTriaje> sesiones = new ConcurrentHashMap<>();

        @Override
        public SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial) {
            String id = UUID.randomUUID().toString();
            SesionTriaje sesion = new SesionTriaje(id, estado, pasoInicial);
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
    }

    @BeforeEach
    void preparar() {
        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
        BaseReglas base = BaseReglasMapper.desde(contenido.reglas(), grafo);
        ContextoClinico contexto = mock(ContextoClinico.class);
        when(contexto.getGrafo()).thenReturn(grafo);
        when(contexto.getBase()).thenReturn(base);

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
    void retrocederQuedaGuardadoYLaConsultaLoRefleja() {
        String id = hastaUrgencia();
        servicio.responder(id, "No, ninguno de los anteriores");

        RecorridoDTO atras = servicio.retroceder(id);

        assertEquals("EN_CURSO", atras.estado());
        assertEquals(URGENCIA, atras.pregunta().id());
        assertEquals(URGENCIA, servicio.consultar(id).pregunta().id());
    }

    @Test
    void trasRetrocederSePuedeCambiarLaRespuesta() {
        String id = hastaUrgencia();
        assertEquals("EN_CURSO", servicio.responder(id, "No, ninguno de los anteriores").estado());
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
