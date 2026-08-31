package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests del TAD ReglaClinica (migrados de tests/test_regla_clinica.py). */
class ReglaClinicaTest {

    private static Nodo destino() {
        return new Nodo("urg", "consulta_medica", "Urgencia", 10);
    }

    @Test
    void creacion() {
        ReglaClinica r = new ReglaClinica(
                "r1",
                "Fiebre alta",
                List.of(new Condicion("fiebre", "Sí"), new Condicion("temp", "≥38°C")),
                destino(),
                8);
        assertEquals("r1", r.getIdRegla());
        assertEquals("Fiebre alta", r.getNombre());
        assertEquals(8, r.getPeso());
        assertEquals("urg", r.getNodoDestino().getId());
        assertEquals(
                List.of(new Condicion("fiebre", "Sí"), new Condicion("temp", "≥38°C")),
                r.getCondiciones());
    }

    @Test
    void condicionesVaciasFalla() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReglaClinica("r1", "n", List.of(), destino()));
    }

    @Test
    void condicionesCumplidasTodas() {
        ReglaClinica r = new ReglaClinica("r1", "n",
                List.of(new Condicion("fiebre", "Sí"), new Condicion("temp", "≥38°C")),
                destino());
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "Sí");
        e.registrarRespuesta("temp", "≥38°C");
        assertTrue(r.condicionesCumplidas(e));
    }

    @Test
    void condicionesNoCumplidasUnaFalta() {
        ReglaClinica r = new ReglaClinica("r1", "n",
                List.of(new Condicion("fiebre", "Sí"), new Condicion("temp", "≥38°C")),
                destino());
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "Sí");
        assertFalse(r.condicionesCumplidas(e));
    }

    @Test
    void condicionesNoCumplidasRespuestaDistinta() {
        ReglaClinica r = new ReglaClinica("r1", "n",
                List.of(new Condicion("fiebre", "Sí")), destino());
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "No");
        assertFalse(r.condicionesCumplidas(e));
    }

    @Test
    void igualdadPorId() {
        ReglaClinica r1 = new ReglaClinica("mismo", "a",
                List.of(new Condicion("x", "y")), destino());
        ReglaClinica r2 = new ReglaClinica("mismo", "b",
                List.of(new Condicion("otro", "otro")), destino());
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void condicionesEsCopia() {
        ReglaClinica r = new ReglaClinica("r1", "n",
                List.of(new Condicion("fiebre", "Sí")), destino());
        List<Condicion> c = r.getCondiciones();
        c.add(new Condicion("hack", "hack"));
        assertEquals(1, r.getCondiciones().size());
    }
}
