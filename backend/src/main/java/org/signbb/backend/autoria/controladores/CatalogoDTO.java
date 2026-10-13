package org.signbb.backend.autoria.controladores;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Catálogo de nodos de un grafo, para que el editor de reglas ofrezca nodos y
 * respuestas válidos. Para un subgrafo los ids son locales; para el grafo raíz
 * vienen ya prefijados con el grafo de cada categoría.
 */
@Schema(description = "Catálogo de nodos y respuestas de un grafo.")
public record CatalogoDTO(
        @Schema(description = "Grafo del catálogo") String grafoId,
        @Schema(description = "Nodos del grafo") List<NodoCatalogoDTO> nodos) {

    /** Nodo del catálogo con sus respuestas. */
    @Schema(description = "Nodo del catálogo con sus respuestas.")
    public record NodoCatalogoDTO(
            @Schema(description = "Id del nodo") String id,
            @Schema(description = "Texto del nodo") String texto,
            @Schema(description = "pregunta o terminal") String tipo,
            @Schema(description = "Respuestas válidas; vacía en un terminal") List<String> opciones,
            @Schema(description = "Desenlace del terminal (RECOMENDACION, CONSULTA_MEDICA o URGENCIA); "
                    + "nulo en las preguntas")
            String desenlace) {
    }
}
