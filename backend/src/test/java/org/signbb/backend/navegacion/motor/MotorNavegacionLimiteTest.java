package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests del máximo de preguntas del motor de navegación sobre una cadena de
 * 12 preguntas ({@code p1 --SI--> p2 --SI--> ... p12}, cada una con
 * {@code NO} hacia una consulta y {@code p12 --SI-->} un terminal), más
 * larga que el límite por defecto de 10.
 */
class MotorNavegacionLimiteTest {

    private static final int LARGO = 12;

    private GrafoClinico grafo;
    private BaseReglas sinReglas;
    private MotorNavegacion motor;

    @BeforeEach
    void construir() {
        grafo = new GrafoClinico();
        Nodo[] preguntas = new Nodo[LARGO + 1];
        for (int i = 1; i <= LARGO; i++) {
            preguntas[i] = new Nodo("p" + i, "pregunta", "¿Signo " + i + "?", 1);
            grafo.agregarNodo(preguntas[i]);
        }
        Nodo consulta = new Nodo("consulta", "consulta_medica", "Consulte", 2);
        Nodo fin = new Nodo("fin", "recomendacion", "Todo bien", 1);
        Nodo urgencia = new Nodo("urgencia", "urgencia", "Urgencias", 3);
        grafo.agregarNodo(consulta);
        grafo.agregarNodo(fin);
        grafo.agregarNodo(urgencia);
        for (int i = 1; i <= LARGO; i++) {
            grafo.conectarRespuestas(preguntas[i], i < LARGO ? preguntas[i + 1] : fin, "SI");
            grafo.conectarRespuestas(preguntas[i], consulta, "NO");
        }
        sinReglas = new BaseReglas();
        motor = new MotorNavegacion(new ServicioReglasImpl());
    }

    /** Responde SI a las {@code n} primeras preguntas y devuelve el último paso. */
    private ResultadoPaso responderSi(MotorNavegacion m, BaseReglas base, EstadoPaciente e, int n) {
        ResultadoPaso paso = m.iniciar(e, grafo);
        for (int i = 0; i < n; i++) {
            paso = m.paso(grafo, base, e, paso.preguntaActual(), "SI");
        }
        return paso;
    }

    @Test
    void antesDelLimiteElRecorridoSigue() {
        ResultadoPaso paso = responderSi(motor, sinReglas, new EstadoPaciente("s"), 9);

        assertFalse(paso.finalizado());
        assertEquals("p10", paso.preguntaActual().getId());
    }

    @Test
    void alLlegarAlLimiteCierraConRecomendacionNormal() {
        ResultadoPaso paso = responderSi(motor, sinReglas, new EstadoPaciente("s"), 10);

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertEquals(MotorNavegacion.LIMITE_PREGUNTAS, paso.destino());
        assertTrue(paso.razones().isEmpty());
        // El camino causal llega hasta la pregunta que quedó sin responder.
        assertEquals("p11", paso.caminoCausal().get(paso.caminoCausal().size() - 1).nodo().getId());
    }

    @Test
    void unTerminalEnLaDecimaRespuestaGanaAlLimite() {
        EstadoPaciente e = new EstadoPaciente("s");
        ResultadoPaso p10 = responderSi(motor, sinReglas, e, 9);

        ResultadoPaso paso = motor.paso(grafo, sinReglas, e, p10.preguntaActual(), "NO");

        assertEquals("consulta", paso.destino().getId());
        assertEquals(NivelAlerta.CONSULTAR_MEDICO, paso.nivel());
    }

    @Test
    void unaReglaEnLaDecimaRespuestaGanaAlLimite() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(new ReglaClinica("R", "décimo signo", List.of(new Condicion("p10", "SI")),
                grafo.obtenerNodoPorId("urgencia").orElseThrow(), NivelAlerta.URGENCIA));

        ResultadoPaso paso = responderSi(motor, base, new EstadoPaciente("s"), 10);

        assertEquals("urgencia", paso.destino().getId());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
    }

    @Test
    void elLimiteEsConfigurable() {
        MotorNavegacion corto = new MotorNavegacion(new ServicioReglasImpl(), 3);

        assertFalse(responderSi(corto, sinReglas, new EstadoPaciente("a"), 2).finalizado());
        assertEquals(MotorNavegacion.LIMITE_PREGUNTAS,
                responderSi(corto, sinReglas, new EstadoPaciente("b"), 3).destino());
    }

    @Test
    void limiteMenorQueUnoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> new MotorNavegacion(new ServicioReglasImpl(), 0));
    }

    @Test
    void lasRespuestasDeshechasNoCuentanParaElLimite() {
        EstadoPaciente e = new EstadoPaciente("s");
        responderSi(motor, sinReglas, e, 9);
        Nodo p9 = motor.retroceder(grafo, sinReglas, e).preguntaActual();

        // Al responder de nuevo p9 van 9 respondidas, no 10: el recorrido sigue.
        ResultadoPaso p10 = motor.paso(grafo, sinReglas, e, p9, "SI");
        assertFalse(p10.finalizado());
        assertEquals("p10", p10.preguntaActual().getId());

        assertEquals(MotorNavegacion.LIMITE_PREGUNTAS,
                motor.paso(grafo, sinReglas, e, p10.preguntaActual(), "SI").destino());
    }

    @Test
    void retrocederDesdeElLimiteVuelveALaDecimaPregunta() {
        EstadoPaciente e = new EstadoPaciente("s");
        responderSi(motor, sinReglas, e, 10);

        ResultadoPaso atras = motor.retroceder(grafo, sinReglas, e);

        assertFalse(atras.finalizado());
        assertEquals("p10", atras.preguntaActual().getId());
        assertEquals(9, e.size());
    }
}
