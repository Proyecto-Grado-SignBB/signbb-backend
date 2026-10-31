package org.signbb.backend.navegacion.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests del TAD Respuesta (migrados de tests/test_respuesta.py). */
class RespuestaTest {

    private static Nodo n(String id) {
        return new Nodo(id, "pregunta", "contenido-" + id, 0);
    }

    @Test
    void condicionVaciaFalla() {
        assertThrows(IllegalArgumentException.class, () -> new Respuesta(n("a"), n("b"), ""));
    }

    @Test
    void igualdad() {
        Nodo a = n("a");
        Nodo b = n("b");
        Respuesta r1 = new Respuesta(a, b, "Sí");
        Respuesta r2 = new Respuesta(a, b, "Sí");
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}
