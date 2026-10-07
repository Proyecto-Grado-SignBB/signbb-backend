package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.PasoRecorrido;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.servicios.ServicioReglas;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests de {@link MotorNavegacion#retroceder} sobre el contenido de ejemplo
 * compuesto (raíz + subgrafo de ictericia). Cada respuesta apila un
 * {@link PasoRecorrido} con las reglas que marcó; retroceder lo desapila,
 * borra esa respuesta, desmarca solo esas reglas y vuelve a la pregunta sin
 * reevaluar nada. Sin pila (sesión guardada antes de existir) el motor
 * reproduce el recorrido. Los casos con la cola de la selección múltiple
 * están en {@link MotorNavegacionColaTest}.
 */
class MotorNavegacionRetrocederTest {

    private static final String ICTERICIA = "grafo_piel_ictericia";

    private GrafoClinico grafo;
    private BaseReglas base;
    private MotorNavegacion motor;

    @BeforeEach
    void cargar() {
        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        grafo = GrafoClinicoMapper.desde(contenido.grafo());
        base = BaseReglasMapper.desde(contenido.reglas(), grafo);
        motor = new MotorNavegacion(new ServicioReglasImpl());
    }

    private static String id(String idLocal) {
        return CompositorGrafoClinico.prefijar(ICTERICIA, idLocal);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    /** Recorre raíz → piel → coloración amarilla y devuelve la primera pregunta del subgrafo. */
    private Nodo hastaColoracion(MotorNavegacion m, EstadoPaciente e) {
        Nodo inicial = m.iniciar(e, grafo).preguntaActual();
        Nodo puente = m.paso(grafo, base, e, inicial, "piel").preguntaActual();
        return m.paso(grafo, base, e, puente, "coloracion_amarilla").preguntaActual();
    }

    /** Lleva el recorrido hasta la urgencia de R1 (coloración SI + SOMNOLENCIA). */
    private ResultadoPaso hastaUrgenciaR1(EstadoPaciente e) {
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(motor, e), "SI").preguntaActual();
        return motor.paso(grafo, base, e, signos, List.of("SOMNOLENCIA"));
    }

    @Test
    void vuelveALaPreguntaAnteriorBorraSuRespuestaYPermiteOtroCamino() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = hastaColoracion(motor, e);
        motor.paso(grafo, base, e, coloracion, "SI");

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertFalse(atras.finalizado());
        assertEquals(id("pregunta_coloracion"), atras.preguntaActual().getId());
        assertTrue(e.getRespuestas(coloracion.getId()).isEmpty());
        assertEquals(List.of("piel"), e.getRespuestas("pregunta_inicial"), "las anteriores se conservan");

        ResultadoPaso conNo = motor.paso(grafo, base, e, atras.preguntaActual(), "NO");
        assertEquals(id("recomendacion_lactancia"), conNo.destino().getId());
        assertEquals(NivelAlerta.NORMAL, conNo.nivel());
    }

    @Test
    void cadaRespuestaApilaUnPasoConLasReglasQueMarco() {
        EstadoPaciente e = new EstadoPaciente("s");
        hastaUrgenciaR1(e);

        List<PasoRecorrido> pasos = e.pasos();
        assertEquals(4, pasos.size());
        PasoRecorrido cima = pasos.get(3);
        assertEquals(id("pregunta_signos"), cima.idPregunta());
        assertEquals(List.of("SOMNOLENCIA"), cima.respuestas());
        assertEquals(List.of(id("R1")), cima.reglasMarcadas());
        assertTrue(pasos.get(2).reglasMarcadas().isEmpty(), "la coloración sola no dispara reglas");
    }

    @Test
    void deshaceSoloLasReglasDeEsePasoYSePuedenVolverADisparar() {
        EstadoPaciente e = new EstadoPaciente("s");
        assertEquals(NivelAlerta.URGENCIA, hastaUrgenciaR1(e).nivel());

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals(id("pregunta_signos"), atras.preguntaActual().getId());
        assertFalse(e.reglaDisparada(id("R1")), "la regla de la respuesta deshecha ya no está disparada");
        assertEquals(List.of("SI"), e.getRespuestas(id("pregunta_coloracion")));
        assertEquals(3, e.pasos().size());

        // Con otra respuesta la alerta desaparece...
        ResultadoPaso ninguno = motor.paso(grafo, base, e, atras.preguntaActual(), List.of("NINGUNO"));
        assertEquals(NivelAlerta.NORMAL, ninguno.nivel());
        assertTrue(ninguno.razones().isEmpty());

        // ...y si se vuelve a marcar el mismo signo, R1 dispara otra vez.
        ResultadoPaso otraVez = motor.paso(grafo, base, e,
                motor.retroceder(grafo, base, e).preguntaActual(), List.of("SOMNOLENCIA"));
        assertEquals(NivelAlerta.URGENCIA, otraVez.nivel());
        assertEquals(List.of(id("R1")), idsRegla(otraVez.razones()));
    }

    @Test
    void noReevaluaLasReglas() {
        ServicioReglas reglas = spy(new ServicioReglasImpl());
        MotorNavegacion conEspia = new MotorNavegacion(reglas);
        EstadoPaciente e = new EstadoPaciente("s");
        conEspia.paso(grafo, base, e, hastaColoracion(conEspia, e), "SI");
        clearInvocations(reglas);

        ResultadoPaso atras = conEspia.retroceder(grafo, base, e);

        assertEquals(id("pregunta_coloracion"), atras.preguntaActual().getId());
        verify(reglas, never()).evaluar(any(), any());
    }

    @Test
    void dosVecesSeguidasVuelveDosPreguntas() {
        EstadoPaciente e = new EstadoPaciente("s");
        motor.paso(grafo, base, e, hastaColoracion(motor, e), "SI");

        motor.retroceder(grafo, base, e);
        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals("puente_piel", atras.preguntaActual().getId());
        assertEquals(1, e.size());
        assertEquals(1, e.pasos().size());
    }

    @Test
    void sinRespuestasSeRechaza() {
        EstadoPaciente e = new EstadoPaciente("s");
        motor.iniciar(e, grafo);

        assertThrows(IllegalArgumentException.class, () -> motor.retroceder(grafo, base, e));
    }

    @Test
    void unaPreguntaQueYaNoExisteEnElContenidoFallaConEstadoIlegal() {
        // Paso guardado contra una versión anterior del contenido clínico.
        EstadoPaciente e = new EstadoPaciente("s");
        e.registrarRespuesta("pregunta_retirada", "SI");
        e.apilarPaso(new PasoRecorrido("pregunta_retirada", List.of("SI"), List.of(), List.of()));

        assertThrows(IllegalStateException.class, () -> motor.retroceder(grafo, base, e));
    }

    @Test
    void sinPilaReproduceElRecorridoYReconstruyeLaPila() {
        // Estado armado a mano, como una sesión guardada antes de existir la pila.
        EstadoPaciente e = new EstadoPaciente("s");
        e.registrarRespuesta("pregunta_inicial", "piel");
        e.registrarRespuesta("puente_piel", "coloracion_amarilla");

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals("puente_piel", atras.preguntaActual().getId());
        assertTrue(e.getRespuestas("puente_piel").isEmpty());
        assertEquals(1, e.pasos().size());
        assertEquals("pregunta_inicial", e.pasos().get(0).idPregunta());
    }

    @Test
    void sinPilaYConElContenidoCambiadoFallaConEstadoIlegal() {
        // La respuesta guardada ya no está en el camino que da el contenido actual.
        EstadoPaciente e = new EstadoPaciente("s");
        e.registrarRespuesta("pregunta_inicial", "piel");
        e.registrarRespuesta("pregunta_retirada", "SI");

        assertThrows(IllegalStateException.class, () -> motor.retroceder(grafo, base, e));
    }
}
