package org.signbb.backend.autoria.controladores;

import java.time.Instant;
import java.util.List;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.servicios.ValidadorLienzo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Borrador completo: sus datos, el lienzo y los avisos. Lo devuelven todas las
 * operaciones que editan, para que el front solo tenga que redibujar.
 *
 * @param id             id del borrador
 * @param grafoId        id del subgrafo publicado ({@code grafo_...})
 * @param nombre         nombre del subgrafo
 * @param puenteId       pregunta puente del grafo raíz donde se ofrece
 * @param categoria      categoría del subgrafo
 * @param etiquetaOpcion texto de la opción en la pregunta puente
 * @param autor          autor del contenido
 * @param institucion    institución del autor
 * @param estado         borrador, publicado o con cambios sin publicar
 * @param inicio         id de la primera pregunta, o {@code null}
 * @param nodos          nodos del lienzo en el orden en que se crearon
 * @param avisos         avisos actuales del lienzo
 * @param creadoEn       fecha de creación
 * @param actualizadoEn  fecha de la última edición
 * @param publicadoEn    fecha de la última publicación, o {@code null}
 * @param puedeDescartar {@code true} si hay una copia de lo publicado a la que se puede volver
 */
@Schema(description = "Borrador completo: sus datos, el lienzo y los avisos. Lo devuelven "
        + "todas las operaciones que editan.")
public record SubgrafoDTO(
        @Schema(description = "Id del borrador") Long id,
        @Schema(description = "Id del subgrafo publicado (grafo_...)") String grafoId,
        @Schema(description = "Nombre técnico del subgrafo") String nombre,
        @Schema(description = "Pregunta puente del grafo raíz donde se ofrece") String puenteId,
        @Schema(description = "Categoría del subgrafo") String categoria,
        @Schema(description = "Texto de la opción en la pregunta puente") String etiquetaOpcion,
        @Schema(description = "Autor del contenido") String autor,
        @Schema(description = "Institución del autor") String institucion,
        @Schema(description = "Estado: BORRADOR, PUBLICADO o CAMBIOS_SIN_PUBLICAR") EstadoBorrador estado,
        @Schema(description = "Id de la primera pregunta, o nulo") String inicio,
        @Schema(description = "Nodos del lienzo en el orden en que se crearon") List<NodoLienzo> nodos,
        @Schema(description = "Avisos actuales del lienzo") List<Aviso> avisos,
        @Schema(description = "Fecha de creación") Instant creadoEn,
        @Schema(description = "Fecha de la última edición") Instant actualizadoEn,
        @Schema(description = "Fecha de la última publicación, o nula") Instant publicadoEn,
        @Schema(description = "true si hay una copia de lo publicado a la que se puede volver")
        boolean puedeDescartar) {

    /** DTO de un borrador, con sus avisos calculados en el momento. */
    public static SubgrafoDTO desde(BorradorSubgrafoEntity b) {
        return new SubgrafoDTO(b.getId(), b.getGrafoId(), b.getNombre(), b.getPuenteId(), b.getCategoria(),
                b.getEtiquetaOpcion(), b.getAutor(), b.getInstitucion(), b.getEstado(),
                b.getDocumento().inicio(), List.copyOf(b.getDocumento().nodos().values()),
                ValidadorLienzo.validar(b.getDocumento()), b.getCreadoEn(), b.getActualizadoEn(),
                b.getPublicadoEn(), b.getCopiaPublicada() != null);
    }
}
