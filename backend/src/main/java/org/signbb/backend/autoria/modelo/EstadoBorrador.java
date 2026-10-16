package org.signbb.backend.autoria.modelo;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado de un borrador respecto a lo que ve la cuidadora. */
@Schema(description = "Estado de un borrador respecto a lo que ve la cuidadora.")
public enum EstadoBorrador {

    /** Nunca se publicó. */
    @Schema(description = "No está publicado: nunca se publicó o se despublicó.")
    BORRADOR,

    /** Lo publicado coincide con el borrador. */
    @Schema(description = "Lo publicado coincide con el borrador.")
    PUBLICADO,

    /** Se publicó y después se editó: la cuidadora sigue viendo la versión anterior. */
    @Schema(description = "Se publicó y después se editó: la cuidadora sigue viendo la versión anterior.")
    CAMBIOS_SIN_PUBLICAR,

    /**
     * El autor lo envió a revisión: nadie lo edita hasta que un supervisor lo
     * apruebe (y se publique) o lo rechace, o el autor lo retire.
     */
    @Schema(description = "Enviado a revisión: no se edita hasta que un supervisor lo apruebe o lo "
            + "rechace, o el autor lo retire.")
    EN_REVISION
}
