package org.signbb.backend.navegacion.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void puntaje() {
        EstadoPaciente e = new EstadoPaciente("s1");
        e.sumarPuntaje(5);
        e.sumarPuntaje(3);
        assertEquals(8, e.getPuntaje());
    }

    @Test
    void desapilarBorraLaRespuestaYDesmarcaSoloSusReglas() {
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("p1", "SI");
        e.marcarReglaDisparada("R0");
        e.apilarPaso(new PasoRecorrido("p1", List.of("SI"), List.of(), List.of()));
        e.registrarRespuesta("p2", "SI");
        e.marcarReglaDisparada("R1");
        e.apilarPaso(new PasoRecorrido("p2", List.of("SI"), List.of(), List.of("R1")));

        PasoRecorrido deshecho = e.desapilarPaso().orElseThrow();

        assertEquals("p2", deshecho.idPregunta());
        assertTrue(e.getRespuestas("p2").isEmpty());
        assertFalse(e.reglaDisparada("R1"));
        assertTrue(e.reglaDisparada("R0"));
        assertEquals("p1", e.pasos().get(0).idPregunta());
    }

    @Test
    void desapilarRestauraLasRespuestasPrevias() {
        // Una compuerta llevó a responder de nuevo p1: al deshacer vuelve la respuesta vieja.
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("p1", "SI");
        e.apilarPaso(new PasoRecorrido("p1", List.of("SI"), List.of(), List.of()));
        e.registrarRespuesta("p1", "NO");
        e.apilarPaso(new PasoRecorrido("p1", List.of("NO"), List.of("SI"), List.of()));

        e.desapilarPaso();

        assertEquals(List.of("SI"), e.getRespuestas("p1"));
        assertEquals(1, e.pasos().size());
    }
}
