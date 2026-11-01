package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests de {@link MotorNavegacion#retroceder} sobre el contenido real del seed,
 * recorriendo actividad y color. Cada respuesta apila un paso con las reglas
 * que marcó; retroceder lo desapila, borra esa respuesta, desmarca solo esas
 * reglas y vuelve a la pregunta anterior.
 */
class MotorNavegacionRetrocederTest {

    private static final String ACTIVO = "grafo_activo_color";
    private static final String CATEGORIA = "Actividad y color";
    private static final String REVISAR = "Revisar actividad y color";
    private static final String SI = "Sí";
    private static final String MOVIMIENTO = "Movimiento y tono";
    private static final String ALARMA = "Mucho más dormido / cuesta despertarlo";
    private static final String NORMAL = "Sobresaltos mientras duerme y luego normal";

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
        return CompositorGrafoClinico.prefijar(ACTIVO, idLocal);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    /** Raíz → categoría → puente → área → pregunta de tono. */
    private Nodo hastaTono(MotorNavegacion m, EstadoPaciente e) {
        Nodo inicial = m.iniciar(e, grafo).preguntaActual();
        Nodo puente = m.paso(grafo, base, e, inicial, CATEGORIA).preguntaActual();
        Nodo puerta = m.paso(grafo, base, e, puente, REVISAR).preguntaActual();
        Nodo areas = m.paso(grafo, base, e, puerta, SI).preguntaActual();
        return m.paso(grafo, base, e, areas, MOVIMIENTO).preguntaActual();
    }

    @Test
    void vuelveALaPreguntaAnteriorBorraSuRespuestaYPermiteOtroCamino() {
        EstadoPaciente e = new EstadoPaciente();
        Nodo tono = hastaTono(motor, e);
        assertEquals(NivelAlerta.NORMAL,
                motor.paso(grafo, base, e, tono, NORMAL).nivel());

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertFalse(atras.finalizado());
        assertEquals(id("q_neuro_tono"), atras.preguntaActual().getId());
        assertTrue(e.getRespuestas(tono.getId()).isEmpty());
        assertEquals(List.of(CATEGORIA), e.getRespuestas("pregunta_inicial"),
                "las respuestas anteriores se conservan");

        ResultadoPaso conAlarma = motor.paso(grafo, base, e, atras.preguntaActual(), ALARMA);
        assertEquals(id("T_URGENCIA"), conAlarma.destino().getId());
        assertEquals(NivelAlerta.URGENCIA, conAlarma.nivel());
    }

    @Test
    void deshaceSoloLasReglasDeEsePasoYSePuedenVolverADisparar() {
        EstadoPaciente e = new EstadoPaciente();
        assertEquals(NivelAlerta.URGENCIA,
                motor.paso(grafo, base, e, hastaTono(motor, e), ALARMA).nivel());

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals(id("q_neuro_tono"), atras.preguntaActual().getId());
        assertFalse(e.reglaDisparada(id("R_NEURO_URG")),
                "la regla de la respuesta deshecha ya no está disparada");

        ResultadoPaso normal = motor.paso(grafo, base, e, atras.preguntaActual(), NORMAL);
        assertEquals(NivelAlerta.NORMAL, normal.nivel());

        ResultadoPaso otraVez = motor.paso(grafo, base, e,
                motor.retroceder(grafo, base, e).preguntaActual(), ALARMA);
        assertEquals(NivelAlerta.URGENCIA, otraVez.nivel());
        assertEquals(List.of(id("R_NEURO_URG")), idsRegla(otraVez.razones()));
    }

    @Test
    void dosVecesSeguidasVuelveDosPreguntas() {
        EstadoPaciente e = new EstadoPaciente();
        hastaTono(motor, e);

        motor.retroceder(grafo, base, e);
        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals(id("q_neuro_gate"), atras.preguntaActual().getId());
        assertEquals(2, e.pasos().size());
    }

    @Test
    void sinRespuestasSeRechaza() {
        EstadoPaciente e = new EstadoPaciente();
        motor.iniciar(e, grafo);

        assertThrows(IllegalArgumentException.class, () -> motor.retroceder(grafo, base, e));
    }
}
