package org.signbb.backend.reglas.modelo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;

/**
 * Evaluación de reglas con datos abstractos ({@code resp1/op1/op2/op3}) y los
 * TADs {@link EstadoPaciente} y {@link Nodo} simulados con Mockito: no depende
 * del dominio ni de la base de datos.
 *
 * <p>
 * Conserva los dos casos que no cubren las otras suites: varios casos (OR de la
 * FND) y varias opciones de la misma pregunta (AND).
 */
class EvaluacionReglasAbstractaTest {

    private static final String RESP = "resp1";

    private static Nodo destino(String id, int peso) {
        Nodo destino = mock(Nodo.class);
        when(destino.getId()).thenReturn(id);
        when(destino.getPeso()).thenReturn(peso);
        return destino;
    }

    /** Estado simulado que ya marcó las respuestas indicadas en {@code resp1}. */
    private static EstadoPaciente estadoCon(String... respuestas) {
        EstadoPaciente estado = mock(EstadoPaciente.class);
        for (String r : respuestas) {
            when(estado.contieneRespuesta(RESP, r)).thenReturn(true);
        }
        return estado;
    }

    @Test
    void variosCasosSeCombinanConO() {
        ReglaClinica r1 = ReglaClinica.conCasos("R1", "abstracta",
                List.of(new Caso(List.of(new Condicion(RESP, "op1"))),
                        new Caso(List.of(new Condicion(RESP, "op2"), new Condicion(RESP, "op3")))),
                destino("dest", 3), NivelAlerta.URGENCIA);

        assertTrue(r1.condicionesCumplidas(estadoCon("op1")));
        assertTrue(r1.condicionesCumplidas(estadoCon("op2", "op3")));
        assertFalse(r1.condicionesCumplidas(estadoCon("op2")));
    }

    @Test
    void variasOpcionesDeLaMismaPreguntaSeCombinanConAnd() {
        ReglaClinica r1 = new ReglaClinica("R1", "abstracta",
                List.of(new Condicion(RESP, "op1"), new Condicion(RESP, "op2")),
                destino("dest", 2), NivelAlerta.CONSULTAR_MEDICO);

        assertTrue(r1.condicionesCumplidas(estadoCon("op1", "op2")));
        assertFalse(r1.condicionesCumplidas(estadoCon("op1")));
    }
}
