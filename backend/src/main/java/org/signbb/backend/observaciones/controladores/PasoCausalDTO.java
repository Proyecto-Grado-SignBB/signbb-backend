package org.signbb.backend.observaciones.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un paso del camino causal hacia el desenlace: el {@code nodoId} alcanzado y
 * la {@code condicion} de la arista por la que se llegó (vacía para el nodo
 * inicial).
 */
@Schema(description = "Un paso del camino causal hacia el desenlace.")
public record PasoCausalDTO(
        @Schema(description = "Id del nodo alcanzado") String nodoId,
        @Schema(description = "Condición de la arista por la que se llegó; vacía para el nodo inicial")
        String condicion) {
}
