package org.signbb.backend.observaciones.controladores;

import java.util.List;

/**
 * Una recomendación alcanzada durante el recorrido: el {@code nodoId} y el
 * {@code mensaje} del nodo, y el {@code caminoCausal} de preguntas y
 * respuestas que llevó a ella (cada recomendación tiene el suyo).
 */
public record RecomendacionDTO(
                String nodoId,
                String mensaje,
                List<PasoCausalDTO> caminoCausal) {
}
