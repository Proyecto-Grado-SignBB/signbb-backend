package org.signbb.backend.reglas.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;

/**
 * Verifica la semántica de {@link ExpresionCondicion}: evaluación de las hojas
 * ({@link Condicion}), de {@code AND} ({@link ExpresionCondicion.Y}), de
 * {@code OR} ({@link ExpresionCondicion.O}), su anidamiento y el aplanado de
 * hojas.
 */
class ExpresionCondicionTest {

    private static EstadoPaciente estado(String... paresIdRespuesta) {
        EstadoPaciente e = new EstadoPaciente("sesion-expr");
        for (int i = 0; i < paresIdRespuesta.length; i += 2) {
            e.registrarRespuesta(paresIdRespuesta[i], paresIdRespuesta[i + 1]);
        }
        return e;
    }

    @Test
    void hojaSeSatisfaceConLaRespuestaExacta() {
        Condicion c = new Condicion("coloracion", "SI");
        assertTrue(c.evaluar(estado("coloracion", "SI")));
        assertFalse(c.evaluar(estado("coloracion", "NO")));
        assertFalse(c.evaluar(estado()));
    }

    @Test
    void andRequiereTodas() {
        ExpresionCondicion and = new ExpresionCondicion.Y(List.of(
                new Condicion("coloracion", "SI"),
                new Condicion("tiempo", "MAS_DE_24_HORAS")));

        assertTrue(and.evaluar(estado("coloracion", "SI", "tiempo", "MAS_DE_24_HORAS")));
        assertFalse(and.evaluar(estado("coloracion", "SI")));
    }

    @Test
    void orRequiereAlguna() {
        ExpresionCondicion or = new ExpresionCondicion.O(List.of(
                new Condicion("letargo", "SI"),
                new Condicion("sangre", "SI")));

        assertTrue(or.evaluar(estado("letargo", "SI")));
        assertTrue(or.evaluar(estado("sangre", "SI")));
        assertFalse(or.evaluar(estado("letargo", "NO", "sangre", "NO")));
    }

    @Test
    void anidamientoAndDeOr() {
        // coloracion=SI AND (letargo=SI OR sangre=SI)
        ExpresionCondicion expr = new ExpresionCondicion.Y(List.of(
                new Condicion("coloracion", "SI"),
                new ExpresionCondicion.O(List.of(
                        new Condicion("letargo", "SI"),
                        new Condicion("sangre", "SI")))));

        assertTrue(expr.evaluar(estado("coloracion", "SI", "sangre", "SI")));
        assertFalse(expr.evaluar(estado("coloracion", "SI")));
        assertFalse(expr.evaluar(estado("letargo", "SI")));
    }

    @Test
    void hojasAplanaEnOrden() {
        ExpresionCondicion expr = new ExpresionCondicion.Y(List.of(
                new Condicion("a", "1"),
                new ExpresionCondicion.O(List.of(
                        new Condicion("b", "2"),
                        new Condicion("c", "3")))));

        assertEquals(
                List.of(new Condicion("a", "1"), new Condicion("b", "2"), new Condicion("c", "3")),
                expr.hojas());
    }

    @Test
    void operandosVaciosSonRechazados() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpresionCondicion.Y(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpresionCondicion.O(List.of()));
    }
}
