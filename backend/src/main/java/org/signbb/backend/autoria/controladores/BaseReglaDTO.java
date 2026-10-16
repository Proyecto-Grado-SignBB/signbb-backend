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
        @Schema(description = "Descripción de la base de reglas") String descripcion,
        @Schema(description = "Autor del contenido") String autor,
        @Schema(description = "Institución del autor") String institucion,
        @Schema(description = "Estado: BORRADOR, PUBLICADO o CAMBIOS_SIN_PUBLICAR") EstadoBorrador estado,
        @Schema(description = "Reglas del borrador, en orden") List<ReglaDTO> reglas,
        @Schema(description = "Avisos actuales") List<Aviso> avisos,
        @Schema(description = "Fecha de creación") Instant creadoEn,
        @Schema(description = "Fecha de la última edición") Instant actualizadoEn,
        @Schema(description = "Fecha de la última publicación, o nula") Instant publicadoEn,
        @Schema(description = "true si hay una copia de lo publicado a la que se puede volver")
        boolean puedeDescartar,
        @Schema(description = "Id de la cuenta del autor, o nulo en bases de antes de las cuentas")
        Long autorId) {

    /** DTO de un borrador, con sus avisos calculados en el momento. */
    public static BaseReglaDTO desde(BorradorBaseReglaEntity b) {
        return desde(b, ValidadorReglas.validar(b.getDocumento()));
    }

    /**
     * DTO de un borrador con los avisos ya calculados (estructura y referencias
     * al lienzo del subgrafo); lo usa el servicio.
     */
    public static BaseReglaDTO desde(BorradorBaseReglaEntity b, List<Aviso> avisos) {
        List<ReglaDTO> reglas = b.getDocumento().reglas().entrySet().stream()
                .map(e -> ReglaDTO.desde(e.getKey(), e.getValue()))
                .toList();
        return new BaseReglaDTO(b.getId(), b.getGrafoId(), b.getNombre(), b.getDescripcion(), b.getAutor(),
                b.getInstitucion(), b.getEstado(), reglas, List.copyOf(avisos),
                b.getCreadoEn(), b.getActualizadoEn(), b.getPublicadoEn(), b.getCopiaPublicada() != null,
                b.getAutorId());
    }
}
