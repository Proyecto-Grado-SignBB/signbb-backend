package org.signbb.backend.reglas.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.model.BaseReglas;
import org.signbb.backend.model.Condicion;
import org.signbb.backend.model.EstadoPaciente;
import org.signbb.backend.model.NivelAlerta;
import org.signbb.backend.model.Nodo;
import org.signbb.backend.model.ReglaClinica;

/** Tests de la resolución de conflicto por severidad del ServicioReglas. */
class ServicioReglasImplTest {

    private final ServicioReglas servicio = new ServicioReglasImpl();

    private static Nodo n(String id) {
        return new Nodo(id, "consulta_medica", "c-" + id, 5);
    }

    private static ReglaClinica regla(String id, NivelAlerta nivel, String idDestino,
            Condicion... condiciones) {
        return new ReglaClinica(id, "regla-" + id, List.of(condiciones), n(idDestino), nivel);
    }

    private static List<String> ids(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    @Test
    void obtenerReglasActivadasRetornaLasDisparadas() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(regla("r1", NivelAlerta.URGENCIA, "d1",
                new Condicion("fiebre", "SI")));
        base.agregarRegla(regla("r2", NivelAlerta.URGENCIA, "d2",
                new Condicion("tos", "SI")));
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "SI");

        assertEquals(List.of("r1"), ids(servicio.obtenerReglasActivadas(base, e)));
    }

    @Test
    void ningunaActivaDevuelveNormalImplicito() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(regla("r1", NivelAlerta.URGENCIA, "d1",
                new Condicion("fiebre", "SI")));
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "NO");

        ResultadoInferencia r = servicio.evaluar(base, e);

        assertTrue(r.esNormal());
        assertNull(r.destino());
        assertTrue(r.reglas().isEmpty());
    }

    @Test
    void baseVaciaDevuelveNormal() {
        ResultadoInferencia r = servicio.evaluar(new BaseReglas(), new EstadoPaciente("s1"));

        assertTrue(r.esNormal());
    }

    @Test
    void soloConsultarMedicoDevuelveConsultarMedico() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(regla("r1", NivelAlerta.CONSULTAR_MEDICO, "d1",
                new Condicion("fiebre", "SI")));
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "SI");

        ResultadoInferencia r = servicio.evaluar(base, e);

        assertEquals(NivelAlerta.CONSULTAR_MEDICO, r.nivel());
        assertEquals("d1", r.destino().getId());
        assertEquals(List.of("r1"), ids(r.reglas()));
    }

    @Test
    void urgenciaSuperaConsultarMedico() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(regla("r1", NivelAlerta.CONSULTAR_MEDICO, "d1",
                new Condicion("fiebre", "SI")));
        base.agregarRegla(regla("r2", NivelAlerta.URGENCIA, "d2",
                new Condicion("letargo", "SI")));
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "SI");
        e.registrarRespuesta("letargo", "SI");

        ResultadoInferencia r = servicio.evaluar(base, e);

        assertEquals(NivelAlerta.URGENCIA, r.nivel());
        assertEquals("d2", r.destino().getId());
        assertEquals(List.of("r2"), ids(r.reglas()));
    }

    @Test
    void empateUrgenciaCombinaRazonesYEligePrimeraPorInsercion() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(regla("r1", NivelAlerta.URGENCIA, "d1",
                new Condicion("fiebre", "SI")));
        base.agregarRegla(regla("r2", NivelAlerta.URGENCIA, "d2",
                new Condicion("letargo", "SI")));
        base.agregarRegla(regla("r3", NivelAlerta.CONSULTAR_MEDICO, "d3",
                new Condicion("tos", "SI")));
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "SI");
        e.registrarRespuesta("letargo", "SI");
        e.registrarRespuesta("tos", "SI");

        ResultadoInferencia r = servicio.evaluar(base, e);

        assertEquals(NivelAlerta.URGENCIA, r.nivel());
        assertEquals(List.of("r1", "r2"), ids(r.reglas()));
        assertEquals("d1", r.destino().getId());
    }

    @Test
    void empateConsultarMedicoCombinaRazones() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(regla("r1", NivelAlerta.CONSULTAR_MEDICO, "d1",
                new Condicion("fiebre", "SI")));
        base.agregarRegla(regla("r2", NivelAlerta.CONSULTAR_MEDICO, "d2",
                new Condicion("tos", "SI")));
        EstadoPaciente e = new EstadoPaciente("s1");
        e.registrarRespuesta("fiebre", "SI");
        e.registrarRespuesta("tos", "SI");

        ResultadoInferencia r = servicio.evaluar(base, e);

        assertEquals(NivelAlerta.CONSULTAR_MEDICO, r.nivel());
        assertEquals(List.of("r1", "r2"), ids(r.reglas()));
        assertEquals("d1", r.destino().getId());
    }
}
