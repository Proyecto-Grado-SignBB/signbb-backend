package org.signbb.backend.observaciones.controladores;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pregunta que el recorrido presenta al cuidador mientras está EN_CURSO.
 *
 * <p>
 * {@code opciones} son las respuestas válidas: las etiquetas de las aristas
 * salientes del nodo actual (p. ej. {@code SI}, {@code NO},
 * {@code MAS_DE_24_HORAS}).
 *
 * <p>
 * {@code tieneImagenes} indica si esta pregunta tiene imágenes de apoyo
 * (viene de {@code metadata.archivos} del grafo); {@code imagenes} trae sus
 * rutas ({@code metadata.src}), vacía si {@code tieneImagenes} es false.
 *
 * <p>
 * MODIFICADO: se agrega {@code tipoInteraccion} ({@code SI_NO},
 * {@code SELECCION_UNICA} o {@code SELECCION_MULTIPLE}) para que el
 * frontend sepa qué pantalla renderizar sin tener que inferirlo del número
 * de opciones. Se resuelve en {@link RecorridoDTO#desde}.
 */
@Schema(description = "Pregunta que el recorrido presenta al cuidador mientras está en curso.")
public record PreguntaDTO(
        @Schema(description = "Id del nodo pregunta") String id,
        @Schema(description = "Contenido de la pregunta") String texto,
        @Schema(description = "Etiquetas de las respuestas válidas") List<String> opciones,
        @Schema(description = "Si la pregunta tiene imágenes de apoyo") boolean tieneImagenes,
        @Schema(description = "Rutas de las imágenes; vacía si no tiene") List<String> imagenes,
        @Schema(description = "Tipo de interacción: SI_NO, SELECCION_UNICA o SELECCION_MULTIPLE")
        String tipoInteraccion) {
}