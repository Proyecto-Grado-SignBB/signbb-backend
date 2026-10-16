package org.signbb.backend.seguridad.modelo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Rol de una cuenta. Va en el claim {@code rol} del JWT y Spring Security lo
 * ve como la autoridad {@code ROLE_<rol>}.
 */
@Schema(description = "Rol de la cuenta: PADRE (acudiente), MEDICO (arma subgrafos y reglas) "
        + "o MEDICO_SUPERVISOR (revisa antes de publicar y administra cuentas clínicas)")
public enum Rol {
    /** Padre o acudiente: hace los recorridos de la app de consulta. */
    PADRE,
    /** Médico: arma subgrafos y bases de reglas y los envía a revisión. */
    MEDICO,
    /** Médico supervisor: aprueba o rechaza lo de otros médicos y crea cuentas clínicas. */
    MEDICO_SUPERVISOR;

    /** True si es un rol del equipo clínico (médico o supervisor). */
    public boolean esClinico() {
        return this != PADRE;
    }
}
