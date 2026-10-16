package org.signbb.backend.autoria.controladores;

import java.time.Instant;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.modelo.EstadoBorrador;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila del tablero de subgrafos. */
@Schema(description = "Fila del tablero de subgrafos.")
public record ResumenSubgrafoDTO(
        @Schema(description = "Id del borrador") Long id,
        @Schema(description = "Id del subgrafo publicado") String grafoId,
        @Schema(description = "Nombre técnico del subgrafo") String nombre,
        @Schema(description = "Categoría (pregunta puente) donde se publica") String categoria,
        @Schema(description = "Nombre para la familia que verá la cuidadora") String etiquetaOpcion,
        @Schema(description = "Autor del contenido") String autor,
        @Schema(description = "Estado: BORRADOR, PUBLICADO, CAMBIOS_SIN_PUBLICAR o EN_REVISION")
        EstadoBorrador estado,
        @Schema(description = "Número de nodos del lienzo") int nodos,
        @Schema(description = "Fecha de la última edición") Instant actualizadoEn,
        @Schema(description = "Fecha de la última publicación, o nulo") Instant publicadoEn,
        @Schema(description = "Id de la cuenta del autor, o nulo") Long autorId,
        @Schema(description = "Comentario del supervisor si rechazó la última revisión, o nulo")
        String comentarioRevision,
        @Schema(description = "Cuándo se envió a revisión por última vez, o nulo") Instant enviadoRevisionEn) {

    /** Resumen de un borrador. */
    public static ResumenSubgrafoDTO desde(BorradorSubgrafoEntity b) {
        return new ResumenSubgrafoDTO(b.getId(), b.getGrafoId(), b.getNombre(), b.getCategoria(),
                b.getEtiquetaOpcion(), b.getAutor(), b.getEstado(), b.getDocumento().nodos().size(),
                b.getActualizadoEn(), b.getPublicadoEn(), b.getAutorId(), b.getComentarioRevision(),
                b.getEnviadoRevisionEn());
    }
}
