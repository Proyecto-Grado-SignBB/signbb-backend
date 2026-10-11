package org.signbb.backend.media;

/**
 * Respuesta de subir una imagen.
 *
 * @param nombre nombre con el que quedó guardada; es lo que va en el lienzo y
 *               se pide en {@code /media/{nombre}}
 */
public record ImagenSubidaDTO(String nombre) {
}
