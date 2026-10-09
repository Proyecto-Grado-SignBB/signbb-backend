package org.signbb.backend.autoria.modelo;

/** Estado de un borrador respecto a lo que ve la cuidadora. */
public enum EstadoBorrador {

    /** Nunca se publicó. */
    BORRADOR,

    /** Lo publicado coincide con el borrador. */
    PUBLICADO,

    /** Se publicó y después se editó: la cuidadora sigue viendo la versión anterior. */
    CAMBIOS_SIN_PUBLICAR
}
