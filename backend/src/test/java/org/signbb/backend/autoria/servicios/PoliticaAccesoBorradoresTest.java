package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_A;
import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_B;
import static org.signbb.backend.seguridad.UsuariosPrueba.SUPERVISOR;

import org.junit.jupiter.api.Test;

/**
 * Tests de {@link PoliticaAccesoBorradores}: el autor lee y edita, otro médico
 * no hace nada, el supervisor solo lee, y un borrador sin autor solo lo lee
 * un supervisor.
 */
class PoliticaAccesoBorradoresTest {

    private static final Long DE_A = MEDICO_A.id();

    @Test
    void elAutorLeeYEdita() {
        assertTrue(PoliticaAccesoBorradores.puedeLeer(DE_A, MEDICO_A));
        assertTrue(PoliticaAccesoBorradores.puedeEditar(DE_A, MEDICO_A));
    }

    @Test
    void otroMedicoNiLeeNiEdita() {
        assertFalse(PoliticaAccesoBorradores.puedeLeer(DE_A, MEDICO_B));
        assertFalse(PoliticaAccesoBorradores.puedeEditar(DE_A, MEDICO_B));
    }

    @Test
    void elSupervisorLeePeroNoEditaLoDeOtro() {
        assertTrue(PoliticaAccesoBorradores.puedeLeer(DE_A, SUPERVISOR));
        assertFalse(PoliticaAccesoBorradores.puedeEditar(DE_A, SUPERVISOR));
    }

    @Test
    void unBorradorSinAutorSoloLoLeeUnSupervisor() {
        assertTrue(PoliticaAccesoBorradores.puedeLeer(null, SUPERVISOR));
        assertFalse(PoliticaAccesoBorradores.puedeEditar(null, SUPERVISOR));
        assertFalse(PoliticaAccesoBorradores.puedeLeer(null, MEDICO_A));
        assertFalse(PoliticaAccesoBorradores.puedeEditar(null, MEDICO_A));
    }
}
