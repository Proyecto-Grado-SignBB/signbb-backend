package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.Contexto;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;

/** Tests del TAD Contexto (migrados de tests/test_contexto.py). */
class ContextoTest {

    @Test
    void creacion() {
        Contexto c = new Contexto("p1");
        assertEquals("p1", c.getIdPaciente());
        assertEquals(0, c.size());
        assertTrue(c.sesionActual().isEmpty());
        assertEquals(0, c.getPuntajeGlobal());
    }

    @Test
    void idPacienteVacioFalla() {
        assertThrows(IllegalArgumentException.class, () -> new Contexto(""));
    }

    @Test
    void nuevaSesion() {
        Contexto c = new Contexto("p1");
        EstadoPaciente s = c.nuevaSesion("s1");
        assertEquals("s1", s.getIdSesion());
        assertEquals(1, c.size());
        assertSame(s, c.sesionActual().orElseThrow());
    }

    @Test
    void dosSesionesOrden() {
        Contexto c = new Contexto("p1");
        EstadoPaciente s1 = c.nuevaSesion("s1");
        EstadoPaciente s2 = c.nuevaSesion("s2");
        assertEquals(List.of(s1, s2), c.historial());
        assertSame(s2, c.sesionActual().orElseThrow());
    }

    @Test
    void sesionDuplicadaFalla() {
        Contexto c = new Contexto("p1");
        c.nuevaSesion("s1");
        assertThrows(IllegalArgumentException.class, () -> c.nuevaSesion("s1"));
    }

    @Test
    void historialEsCopia() {
        Contexto c = new Contexto("p1");
        c.nuevaSesion("s1");
        List<EstadoPaciente> h = c.historial();
        h.clear();
        assertEquals(1, c.size());
    }

    @Test
    void puntajeGlobal() {
        Contexto c = new Contexto("p1");
        c.sumarPuntajeGlobal(3);
        c.sumarPuntajeGlobal(7);
        assertEquals(10, c.getPuntajeGlobal());
    }
}
