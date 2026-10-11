package org.signbb.backend.autoria.modelo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Desenlace de una conclusión. {@link #valor()} es el texto que usa el
 * {@code desenlace} de un nodo {@code terminal} en {@code GrafoClinicoJson}.
 */
@Schema(description = "Desenlace de una conclusión.")
public enum Desenlace {

    /** Recomendación en casa. */
    @Schema(description = "Recomendación en casa.")
    RECOMENDACION("recomendacion"),

    /** Consultar al médico. */
    @Schema(description = "Consultar al médico.")
    CONSULTA_MEDICA("consulta_medica"),

    /** Ir a urgencias. */
    @Schema(description = "Ir a urgencias.")
    URGENCIA("urgencia");

    private final String valor;

    /** @param valor valor en el documento del grafo clínico */
    Desenlace(String valor) {
        this.valor = valor;
    }

    /** Valor del desenlace en el documento del grafo clínico. */
    public String valor() {
        return valor;
    }
}
