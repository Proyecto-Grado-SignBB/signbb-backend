package org.signbb.backend.autoria.controladores;

import java.time.Instant;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.modelo.EstadoBorrador;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila del tablero de bases de reglas. */
@Schema(description = "Fila del tablero de bases de reglas.")
public record ResumenBaseReglaDTO(
        @Schema(description = "Id del borrador") Long id,
        @Schema(description = "Grafo publicado al que pertenecen las reglas") String grafoId,
        @Schema(description = "Nombre de la base de reglas") String nombre,
        @Schema(description = "Descripción de la base de reglas") String descripcion,
        @Schema(description = "Autor del contenido") String autor,
        @Schema(description = "Estado: BORRADOR, PUBLICADO o CAMBIOS_SIN_PUBLICAR") EstadoBorrador estado,
        @Schema(description = "Número de reglas") int reglas,
        @Schema(description = "Fecha de la última edición") Instant actualizadoEn,
        @Schema(description = "Fecha de la última publicación, o nula") Instant publicadoEn) {

    /** Resumen de un borrador. */
    public static ResumenBaseReglaDTO desde(BorradorBaseReglaEntity b) {
        return new ResumenBaseReglaDTO(b.getId(), b.getGrafoId(), b.getNombre(), b.getDescripcion(),
                b.getAutor(), b.getEstado(), b.getDocumento().reglas().size(), b.getActualizadoEn(),
                b.getPublicadoEn());
    }
}
