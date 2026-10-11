package org.signbb.backend.autoria.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Categoría en la que se puede publicar un subgrafo: una pregunta puente del
 * grafo raíz.
 *
 * @param puenteId id de la pregunta puente
 * @param nombre   opción de la pregunta inicial que lleva a ella
 * @param pregunta texto de la pregunta puente
 */
@Schema(description = "Categoría en la que se puede publicar un subgrafo: una pregunta puente del grafo raíz.")
public record CategoriaDTO(
        @Schema(description = "Id de la pregunta puente") String puenteId,
        @Schema(description = "Opción de la pregunta inicial que lleva a ella") String nombre,
        @Schema(description = "Texto de la pregunta puente") String pregunta) {
}
