package org.signbb.backend.crobservaciones.controladores;

import java.util.List;

/**
 * Pregunta que el recorrido presenta al cuidador mientras está EN_CURSO.
 *
 * <p>{@code opciones} son las respuestas válidas: las etiquetas de las aristas
 * salientes del nodo actual (p. ej. {@code SI}, {@code NO},
 * {@code MAS_DE_24_HORAS}).
 */
public record PreguntaDTO(String id, String texto, List<String> opciones) {
}
