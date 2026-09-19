package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;

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
                                NivelAlerta.URGENCIA);
                assertEquals("r1", r.getIdRegla());
                assertEquals("Fiebre alta", r.getNombre());
                assertEquals(NivelAlerta.URGENCIA, r.getNivel());
                assertEquals("urg", r.getNodoDestino().getId());
                assertEquals(
                                List.of(new Condicion("fiebre", "Sí"), new Condicion("temp", "≥38°C")),
                                r.getCondiciones());
        }

        @Test
        void condicionesVaciasFalla() {
                assertThrows(IllegalArgumentException.class,
                                () -> new ReglaClinica("r1", "n", List.of(), destino(),
                                                NivelAlerta.CONSULTAR_MEDICO));
        }

        @Test
        void condicionesCumplidasTodas() {
                ReglaClinica r = new ReglaClinica("r1", "n",
                                List.of(new Condicion("fiebre", "Sí"), new Condicion("temp", "≥38°C")),
                                destino(), NivelAlerta.CONSULTAR_MEDICO);
                EstadoPaciente e = new EstadoPaciente("s1");
                e.registrarRespuesta("fiebre", "Sí");
                e.registrarRespuesta("temp", "≥38°C");
                assertTrue(r.condicionesCumplidas(e));
        }

        @Test
        void condicionesNoCumplidasUnaFalta() {
                ReglaClinica r = new ReglaClinica("r1", "n",
                                List.of(new Condicion("fiebre", "Sí"), new Condicion("temp", "≥38°C")),
                                destino(), NivelAlerta.CONSULTAR_MEDICO);
                EstadoPaciente e = new EstadoPaciente("s1");
                e.registrarRespuesta("fiebre", "Sí");
                assertFalse(r.condicionesCumplidas(e));
        }

        @Test
        void condicionesNoCumplidasRespuestaDistinta() {
                ReglaClinica r = new ReglaClinica("r1", "n",
                                List.of(new Condicion("fiebre", "Sí")), destino(),
                                NivelAlerta.CONSULTAR_MEDICO);
                EstadoPaciente e = new EstadoPaciente("s1");
                e.registrarRespuesta("fiebre", "No");
                assertFalse(r.condicionesCumplidas(e));
        }

        @Test
        void igualdadPorId() {
                ReglaClinica r1 = new ReglaClinica("mismo", "a",
                                List.of(new Condicion("x", "y")), destino(), NivelAlerta.CONSULTAR_MEDICO);
                ReglaClinica r2 = new ReglaClinica("mismo", "b",
                                List.of(new Condicion("otro", "otro")), destino(), NivelAlerta.URGENCIA);
                assertEquals(r1, r2);
                assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        void condicionesEsCopia() {
                ReglaClinica r = new ReglaClinica("r1", "n",
                                List.of(new Condicion("fiebre", "Sí")), destino(), NivelAlerta.CONSULTAR_MEDICO);
                List<Condicion> c = r.getCondiciones();
                c.add(new Condicion("hack", "hack"));
                assertEquals(1, r.getCondiciones().size());
        }
}