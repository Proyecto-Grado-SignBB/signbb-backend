package org.signbb.backend.autoria.controladores;

/**
 * Categoría en la que se puede publicar un subgrafo: una pregunta puente del
 * grafo raíz.
 *
 * @param puenteId id de la pregunta puente
 * @param nombre   opción de la pregunta inicial que lleva a ella
 * @param pregunta texto de la pregunta puente
 */
public record CategoriaDTO(String puenteId, String nombre, String pregunta) {
}
