package org.signbb.backend.observaciones.controladores;

import java.util.List;

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
public record PreguntaDTO(
        String id,
        String texto,
        List<String> opciones,
        boolean tieneImagenes,
        List<String> imagenes,
        String tipoInteraccion) {
}