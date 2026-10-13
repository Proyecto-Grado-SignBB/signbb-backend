package org.signbb.backend.autoria.modelo;

/**
 * Copia de un borrador de base de reglas tal como estaba en su última
 * publicación: el documento con las reglas y los datos que también viajan al
 * contenido publicado (nombre, descripción, autor e institución). Permite que
 * el médico descarte sus cambios sin publicar y vuelva a lo que las cuidadoras
 * ven. Se guarda como JSON en la columna {@code copia_publicada} de
 * {@code BorradorBaseReglaEntity}.
 *
 * @param documento    reglas publicadas
 * @param nombre       nombre de la base de reglas
 * @param descripcion  descripción de la base de reglas
 * @param autor        autor del contenido
 * @param institucion  institución del autor
 */
public record CopiaBaseReglaPublicada(
        DocumentoBaseRegla documento,
        String nombre,
        String descripcion,
        String autor,
        String institucion) {
}
