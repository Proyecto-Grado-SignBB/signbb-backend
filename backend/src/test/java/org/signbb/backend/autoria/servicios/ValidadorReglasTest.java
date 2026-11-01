package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.Aviso.Codigo;
import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

/** Tests de {@link ValidadorReglas}: un caso por cada aviso estructural. */
class ValidadorReglasTest {

    private static DocumentoBaseRegla con(BorradorRegla regla) {
        return new DocumentoBaseRegla(1, Map.of("R_1", regla));
    }

    private static BorradorRegla valida() {
        return new BorradorRegla("Fiebre alta", null,
                List.of(new Caso(List.of(new Condicion("pregunta_1", "Más de 39 °C")))),
                NivelAlerta.URGENCIA, "conclusion_5");
    }

    @Test
    void unaReglaCompletaNoDaAvisos() {
        assertTrue(ValidadorReglas.validar(con(valida())).isEmpty());
    }

    @Test
    void unaReglaSinNombreBloquea() {
        List<Aviso> avisos = ValidadorReglas.validar(con(new BorradorRegla("  ", null,
                valida().casos(), NivelAlerta.URGENCIA, "conclusion_5")));
        assertEquals(Codigo.REGLA_SIN_NOMBRE, avisos.get(0).codigo());
        assertTrue(avisos.get(0).bloqueante());
    }

    @Test
    void unaReglaSinCondicionesBloquea() {
        List<Aviso> avisos = ValidadorReglas.validar(con(new BorradorRegla("Fiebre alta", null,
                List.of(), NivelAlerta.URGENCIA, "conclusion_5")));
        assertEquals(Codigo.REGLA_SIN_CONDICIONES, avisos.get(0).codigo());
    }

    @Test
    void unaReglaSinDestinoBloquea() {
        List<Aviso> avisos = ValidadorReglas.validar(con(new BorradorRegla("Fiebre alta", null,
                valida().casos(), NivelAlerta.URGENCIA, null)));
        assertEquals(Codigo.REGLA_SIN_DESTINO, avisos.get(0).codigo());
    }

    @Test
    void unaReglaConNivelNormalBloquea() {
        List<Aviso> avisos = ValidadorReglas.validar(con(new BorradorRegla("Fiebre alta", null,
                valida().casos(), NivelAlerta.NORMAL, "conclusion_5")));
        assertEquals(Codigo.REGLA_NIVEL_NORMAL, avisos.get(0).codigo());
    }
}
