package org.signbb.backend.navegacion.modelo;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests del TAD Nodo (migrados de tests/test_nodo.py). */
class NodoTest {

    @Test
    void creacionTerminales() {
        for (String tipo : new String[] { "recomendacion", "consulta_medica", "urgencia" }) {
            Nodo n = new Nodo("t-" + tipo, tipo, "texto");
            assertTrue(n.esTerminal());
        }
    }

    @Test
    void tipoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Nodo("n1", "otro", "algo"));
    }
}
