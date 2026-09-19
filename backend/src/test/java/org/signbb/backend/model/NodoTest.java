package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.Nodo;

/** Tests del TAD Nodo (migrados de tests/test_nodo.py). */
class NodoTest {

    @Test
    void creacionPregunta() {
        Nodo n = new Nodo("n1", "pregunta", "¿Tiene fiebre?", 5);
        assertEquals("n1", n.getId());
        assertEquals("pregunta", n.getTipo());
        assertEquals("¿Tiene fiebre?", n.getContenido());
        assertEquals(5, n.getPeso());
        assertFalse(n.esTerminal());
    }

    @Test
    void creacionTerminales() {
        for (String tipo : new String[] { "recomendacion", "bloqueo", "consulta_medica" }) {
            Nodo n = new Nodo("t-" + tipo, tipo, "texto");
            assertTrue(n.esTerminal());
        }
    }

    @Test
    void tipoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Nodo("n1", "otro", "algo"));
    }

    @Test
    void idVacio() {
        assertThrows(IllegalArgumentException.class, () -> new Nodo("", "pregunta", "algo"));
    }

    @Test
    void igualdadPorId() {
        Nodo a = new Nodo("mismo", "pregunta", "x", 1);
        Nodo b = new Nodo("mismo", "recomendacion", "y", 99);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void desigualdad() {
        Nodo a = new Nodo("uno", "pregunta", "x");
        Nodo b = new Nodo("dos", "pregunta", "x");
        assertNotEquals(a, b);
    }
}
