package org.signbb.backend.reglas.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;

/** Tests del TAD BaseReglas (migrados de tests/test_base_reglas.py). */
class BaseReglasTest {

    private static Nodo n(String id) {
        return new Nodo(id, "consulta_medica", "c-" + id, 5);
    }

    private static ReglaClinica regla(String id, List<Condicion> condiciones) {
        return new ReglaClinica(id, "regla-" + id, condiciones, n("dest-" + id),
                NivelAlerta.CONSULTAR_MEDICO);
    }

    @Test
    void baseVacia() {
        BaseReglas b = new BaseReglas();
        assertEquals(0, b.size());
        assertTrue(b.todasLasReglas().isEmpty());
    }

    @Test
    void agregarYObtener() {
        BaseReglas b = new BaseReglas();
        ReglaClinica r = regla("r1", List.of(new Condicion("a", "1")));
        b.agregarRegla(r);
        assertEquals(1, b.size());
        assertTrue(b.contains("r1"));
        assertEquals(r, b.obtenerRegla("r1"));
    }

    @Test
    void agregarDuplicadoFalla() {
        BaseReglas b = new BaseReglas();
        b.agregarRegla(regla("r1", List.of(new Condicion("a", "1"))));
        assertThrows(IllegalArgumentException.class,
                () -> b.agregarRegla(regla("r1", List.of(new Condicion("b", "2")))));
    }

    @Test
    void eliminar() {
        BaseReglas b = new BaseReglas();
        b.agregarRegla(regla("r1", List.of(new Condicion("a", "1"))));
        b.eliminarRegla("r1");
        assertFalse(b.contains("r1"));
    }

    @Test
    void eliminarInexistenteFalla() {
        BaseReglas b = new BaseReglas();
        assertThrows(IllegalArgumentException.class, () -> b.eliminarRegla("nope"));
    }

    @Test
    void reglasActivadas() {
        BaseReglas b = new BaseReglas();
        b.agregarRegla(regla("r1", List.of(new Condicion("fiebre", "Sí"))));
        b.agregarRegla(regla("r2",
                List.of(new Condicion("fiebre", "Sí"), new Condicion("letargo", "Sí"))));
        b.agregarRegla(regla("r3", List.of(new Condicion("tos", "Sí"))));

        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "Sí");
        e.registrarRespuesta("letargo", "Sí");

        Set<String> ids = new HashSet<>();
        for (ReglaClinica r : b.reglasActivadas(e)) {
            ids.add(r.getIdRegla());
        }
        assertEquals(Set.of("r1", "r2"), ids);
    }

    @Test
    void reglasActivadasNinguna() {
        BaseReglas b = new BaseReglas();
        b.agregarRegla(regla("r1", List.of(new Condicion("fiebre", "Sí"))));
        EstadoPaciente e = new EstadoPaciente("s1");
        assertTrue(b.reglasActivadas(e).isEmpty());
    }
}