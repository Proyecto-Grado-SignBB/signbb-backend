package org.signbb.backend.navegacion.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.RepositorioSesionMemoria;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests del servicio de navegación (la fachada que usa el controlador REST)
 * sobre el contenido compuesto y el almacén en memoria: abrir un recorrido,
 * responder, consultar y volver atrás guardando siempre la sesión, más los
 * errores de un recorrido inexistente o una respuesta vacía.
 */
class ServicioNavegacionImplTest {

    private static final String COLORACION = "grafo_piel_ictericia/pregunta_coloracion";

    private ServicioNavegacion servicio;

    @BeforeEach
    void preparar() {
        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
        BaseReglas base = BaseReglasMapper.desde(contenido.reglas(), grafo);
        ContextoClinico contexto = mock(ContextoClinico.class);
        when(contexto.getGrafo()).thenReturn(grafo);
        when(contexto.getBase()).thenReturn(base);

        servicio = new ServicioNavegacionImpl(contexto, new MotorNavegacion(new ServicioReglasImpl()),
                new RepositorioSesionMemoria());
    }

    /** Abre un recorrido y lo lleva hasta la pregunta de coloración. */
    private String hastaColoracion() {
        String id = servicio.iniciar().recorridoId();
        servicio.responder(id, "piel");
        servicio.responder(id, "coloracion_amarilla");
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
    void cadaRespuestaQuedaGuardadaYLaConsultaLaRefleja() {
        String id = hastaColoracion();

        assertEquals(COLORACION, servicio.consultar(id).pregunta().id());
        assertEquals("grafo_piel_ictericia/pregunta_signos", servicio.responder(id, "SI").pregunta().id());
        assertEquals("grafo_piel_ictericia/pregunta_signos", servicio.consultar(id).pregunta().id());
    }

    @Test
    void seleccionMultipleConUnaAlertaCierraEnUrgencia() {
        String id = hastaColoracion();
        servicio.responder(id, "SI");

        RecorridoDTO urgencia = servicio.responder(id, List.of("SOMNOLENCIA", "COME_MENOS"));

        assertEquals("FINALIZADO", urgencia.estado());
        assertEquals("URGENCIA", urgencia.resultado().nivel());
        assertEquals("FINALIZADO", servicio.consultar(id).estado());
    }

    @Test
    void unRecorridoFinalizadoNoAceptaMasRespuestas() {
        String id = hastaColoracion();
        RecorridoDTO cierre = servicio.responder(id, "NO");

        RecorridoDTO otraVez = servicio.responder(id, "SI");

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
        String id = hastaColoracion();
        servicio.responder(id, "SI");

        RecorridoDTO atras = servicio.retroceder(id);

        assertEquals("EN_CURSO", atras.estado());
        assertEquals(COLORACION, atras.pregunta().id());
        assertEquals(COLORACION, servicio.consultar(id).pregunta().id());
    }

    @Test
    void trasRetrocederSePuedeCambiarLaRespuesta() {
        String id = hastaColoracion();
        assertEquals("EN_CURSO", servicio.responder(id, "SI").estado());
        servicio.retroceder(id);

        RecorridoDTO conNo = servicio.responder(id, "NO");

        assertEquals("FINALIZADO", conNo.estado());
        assertEquals("NORMAL", conNo.resultado().nivel());
        assertEquals("grafo_piel_ictericia/recomendacion_lactancia", conNo.resultado().destinoId());
    }

    @Test
    void retrocederSinRespuestasSeRechaza() {
        String id = servicio.iniciar().recorridoId();

        assertThrows(IllegalArgumentException.class, () -> servicio.retroceder(id));
    }
}
