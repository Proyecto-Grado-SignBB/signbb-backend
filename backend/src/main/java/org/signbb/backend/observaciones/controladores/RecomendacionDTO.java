package org.signbb.backend.observaciones.controladores;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una recomendación alcanzada durante el recorrido: el {@code nodoId} y el
 * {@code mensaje} del nodo, y el {@code caminoCausal} de preguntas y
 * respuestas que llevó a ella (cada recomendación tiene el suyo).
 */
@Schema(description = "Una recomendación alcanzada durante el recorrido, con su propio camino causal.")
public record RecomendacionDTO(
                @Schema(description = "Id del nodo de recomendación") String nodoId,
                @Schema(description = "Contenido de la recomendación") String mensaje,
                @Schema(description = "Camino de preguntas y respuestas que llevó a ella")
                List<PasoCausalDTO> caminoCausal) {
}
