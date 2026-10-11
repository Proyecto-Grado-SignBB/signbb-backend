package org.signbb.backend.autoria.modelo;

/**
 * Copia de un borrador tal como estaba en su última publicación: el lienzo y
 * los datos que también viajan al grafo publicado (nombre, categoría y texto
 * de la opción). Permite que el médico descarte sus cambios sin publicar y
 * vuelva a lo que las cuidadoras ven. Se guarda como JSON en la columna
 * {@code copia_publicada} de {@code BorradorSubgrafoEntity}.
 *
 * @param documento      lienzo publicado
 * @param nombre         nombre del subgrafo
 * @param puenteId       pregunta puente del grafo raíz
 * @param categoria      categoría de la pregunta puente
 * @param etiquetaOpcion texto de la opción en la pregunta puente
 * @param autor          autor del contenido
 * @param institucion    institución del autor
 */
public record CopiaPublicada(
        DocumentoLienzo documento,
        String nombre,
        String puenteId,
        String categoria,
        String etiquetaOpcion,
        String autor,
        String institucion) {
}
