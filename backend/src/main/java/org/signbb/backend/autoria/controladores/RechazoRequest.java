package org.signbb.backend.autoria.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de {@code POST /api/autoria/subgrafos/{id}/rechazar}.
 *
 * @param comentario qué debe corregir el autor; obligatorio
 */
@Schema(description = "Rechazo de una revisión.")
public record RechazoRequest(
        @Schema(description = "Qué debe corregir el autor (obligatorio)") String comentario) {
}
