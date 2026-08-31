package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests del TAD EstadoPaciente (migrados de tests/test_estado_paciente.py). */
class EstadoPacienteTest {

    @Test
    void creacion() {
        EstadoPaciente e = new EstadoPaciente("s1");
        assertEquals("s1", e.getIdSesion());
        assertEquals(0, e.getPuntaje());
        assertEquals(0, e.size());
    }

    @Test
    void idVacioFalla() {
        assertThrows(IllegalArgumentException.class, () -> new EstadoPaciente(""));
    }

    @Test
    void registrarYConsultarRespuesta() {
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "Sí");
        e.registrarRespuesta("temp", "≥38°C");
        assertEquals(Optional.of("Sí"), e.getRespuesta("fiebre"));
        assertEquals(Optional.of("≥38°C"), e.getRespuesta("temp"));
        assertTrue(e.getRespuesta("no_existe").isEmpty());
        assertEquals(2, e.size());
    }

    @Test
    void registrarSobreescribe() {
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "Sí");
        e.registrarRespuesta("fiebre", "No");
        assertEquals(Optional.of("No"), e.getRespuesta("fiebre"));
    }

    @Test
    void puntaje() {
        EstadoPaciente e = new EstadoPaciente("s1");
        e.sumarPuntaje(5);
        e.sumarPuntaje(3);
        assertEquals(8, e.getPuntaje());
    }

    @Test
    void sintomasEsCopia() {
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("a", "1");
        List<Sintoma> s = e.sintomas();
        s.add(new Sintoma("hack", "hack"));
        assertEquals(1, e.size());
    }

    @Test
    void reiniciar() {
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("a", "1");
        e.sumarPuntaje(10);
        e.reiniciar();
        assertEquals(0, e.size());
        assertEquals(0, e.getPuntaje());
    }
}
