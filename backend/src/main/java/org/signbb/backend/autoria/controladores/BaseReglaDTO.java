package org.signbb.backend.autoria.controladores;

import java.time.Instant;
import java.util.List;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.servicios.ValidadorReglas;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Borrador completo de una base de reglas: sus datos, las reglas y los avisos.
 * Lo devuelven todas las operaciones que editan.
 */
@Schema(description = "Borrador completo de una base de reglas.")
public record BaseReglaDTO(
        @Schema(description = "Id del borrador") Long id,
        @Schema(description = "Grafo publicado al que pertenecen las reglas") String grafoId,
        @Schema(description = "Nombre de la base de reglas") String nombre,
        @Schema(description = "Autor del contenido") String autor,
        @Schema(description = "Institución del autor") String institucion,
        @Schema(description = "Estado: BORRADOR, PUBLICADO o CAMBIOS_SIN_PUBLICAR") EstadoBorrador estado,
        @Schema(description = "Reglas del borrador, en orden") List<ReglaDTO> reglas,
        @Schema(description = "Avisos actuales") List<Aviso> avisos,
        @Schema(description = "Fecha de creación") Instant creadoEn,
        @Schema(description = "Fecha de la última edición") Instant actualizadoEn,
        @Schema(description = "Fecha de la última publicación, o nula") Instant publicadoEn) {

    /** DTO de un borrador, con sus avisos calculados en el momento. */
    public static BaseReglaDTO desde(BorradorBaseReglaEntity b) {
        List<ReglaDTO> reglas = b.getDocumento().reglas().entrySet().stream()
                .map(e -> ReglaDTO.desde(e.getKey(), e.getValue()))
                .toList();
        return new BaseReglaDTO(b.getId(), b.getGrafoId(), b.getNombre(), b.getAutor(), b.getInstitucion(),
                b.getEstado(), reglas, ValidadorReglas.validar(b.getDocumento()), b.getCreadoEn(),
                b.getActualizadoEn(), b.getPublicadoEn());
    }
}
