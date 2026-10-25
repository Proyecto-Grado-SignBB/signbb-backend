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
 * Tests de {@link MotorNavegacion#retroceder} sobre el contenido compuesto
 * (grafo raíz + subgrafos del sistema), recorriendo el de respiración. Cada
 * respuesta apila un {@link PasoRecorrido} con las reglas que marcó;
 * retroceder lo desapila, borra esa respuesta, desmarca solo esas reglas y
 * vuelve a la pregunta sin reevaluar nada. Sin pila no se puede retroceder.
 * Los casos con la cola de la selección múltiple están en
 * {@link MotorNavegacionColaTest}.
 */
class MotorNavegacionRetrocederTest {

    private static final String RESPIRACION = "grafo_respiracion";

    /** Respuesta que descarta los signos de alarma y avanza a la alerta media. */
    private static final String NINGUNO = "No, ninguno de los anteriores";

    /** Signo de alarma que dispara R_RESP_URG (URGENCIA). */
    private static final String QUEJIDO = "Quejido al respirar";

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
        return CompositorGrafoClinico.prefijar(RESPIRACION, idLocal);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    /** Recorre raíz → respiración y devuelve la primera pregunta del subgrafo. */
    private Nodo hastaUrgencia(MotorNavegacion m, EstadoPaciente e) {
        Nodo inicial = m.iniciar(e, grafo).preguntaActual();
        Nodo puente = m.paso(grafo, base, e, inicial, "respiracion").preguntaActual();
        return m.paso(grafo, base, e, puente, "iniciar").preguntaActual();
    }

    @Test
    void vuelveALaPreguntaAnteriorBorraSuRespuestaYPermiteOtroCamino() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo urgencia = hastaUrgencia(motor, e);
        assertEquals(id("q_resp_consulta"), motor.paso(grafo, base, e, urgencia, NINGUNO).preguntaActual().getId());

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertFalse(atras.finalizado());
        assertEquals(id("q_resp_urgencia"), atras.preguntaActual().getId());
        assertTrue(e.getRespuestas(urgencia.getId()).isEmpty());
        assertEquals(List.of("respiracion"), e.getRespuestas("pregunta_inicial"), "las anteriores se conservan");

        ResultadoPaso conQuejido = motor.paso(grafo, base, e, atras.preguntaActual(), QUEJIDO);
        assertEquals(id("urgencia_1"), conQuejido.destino().getId());
        assertEquals(NivelAlerta.URGENCIA, conQuejido.nivel());
    }

    @Test
    void cadaRespuestaApilaUnPasoConLasReglasQueMarco() {
        EstadoPaciente e = new EstadoPaciente("s");
        motor.paso(grafo, base, e, hastaUrgencia(motor, e), QUEJIDO);

        List<PasoRecorrido> pasos = e.pasos();
        assertEquals(3, pasos.size());
        PasoRecorrido cima = pasos.get(2);
        assertEquals(id("q_resp_urgencia"), cima.idPregunta());
        assertEquals(List.of(QUEJIDO), cima.respuestas());
        assertEquals(List.of(id("R_RESP_URG")), cima.reglasMarcadas());
        assertTrue(pasos.get(1).reglasMarcadas().isEmpty(), "el puente no dispara reglas");
    }

    @Test
    void deshaceSoloLasReglasDeEsePasoYSePuedenVolverADisparar() {
        EstadoPaciente e = new EstadoPaciente("s");
        assertEquals(NivelAlerta.URGENCIA, motor.paso(grafo, base, e, hastaUrgencia(motor, e), QUEJIDO).nivel());

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals(id("q_resp_urgencia"), atras.preguntaActual().getId());
        assertFalse(e.reglaDisparada(id("R_RESP_URG")), "la regla de la respuesta deshecha ya no está disparada");
        assertEquals(2, e.pasos().size());

        // Con otra respuesta la alerta desaparece...
        ResultadoPaso ninguno = motor.paso(grafo, base, e, atras.preguntaActual(), NINGUNO);
        assertFalse(ninguno.finalizado());
        assertEquals(id("q_resp_consulta"), ninguno.preguntaActual().getId());

        // ...y si se vuelve a marcar el mismo signo, R_RESP_URG dispara otra vez.
        ResultadoPaso otraVez = motor.paso(grafo, base, e,
                motor.retroceder(grafo, base, e).preguntaActual(), QUEJIDO);
        assertEquals(NivelAlerta.URGENCIA, otraVez.nivel());
        assertEquals(List.of(id("R_RESP_URG")), idsRegla(otraVez.razones()));
    }

    @Test
    void noReevaluaLasReglas() {
        ServicioReglas reglas = spy(new ServicioReglasImpl());
        MotorNavegacion conEspia = new MotorNavegacion(reglas);
        EstadoPaciente e = new EstadoPaciente("s");
        conEspia.paso(grafo, base, e, hastaUrgencia(conEspia, e), QUEJIDO);
        clearInvocations(reglas);

        ResultadoPaso atras = conEspia.retroceder(grafo, base, e);

        assertEquals(id("q_resp_urgencia"), atras.preguntaActual().getId());
        verify(reglas, never()).evaluar(any(), any());
    }

    @Test
    void dosVecesSeguidasVuelveDosPreguntas() {
        EstadoPaciente e = new EstadoPaciente("s");
        motor.paso(grafo, base, e, hastaUrgencia(motor, e), NINGUNO);

        motor.retroceder(grafo, base, e);
        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals("puente_respiracion", atras.preguntaActual().getId());
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
    void sinPilaSeRechazaAunqueHayaRespuestas() {
        // Una sesión heredada sin historial carga con respuestas pero sin pasos.
        EstadoPaciente e = new EstadoPaciente("s");
        e.registrarRespuesta("pregunta_inicial", "respiracion");
        e.registrarRespuesta("puente_respiracion", "iniciar");

        assertThrows(IllegalArgumentException.class, () -> motor.retroceder(grafo, base, e));
    }
}
