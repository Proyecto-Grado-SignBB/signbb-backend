package org.signbb.backend.autoria.controladores;

import java.time.Instant;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.modelo.EstadoBorrador;

/** Fila del tablero de subgrafos. */
public record ResumenSubgrafoDTO(
        Long id,
        String grafoId,
        String nombre,
        String categoria,
        String etiquetaOpcion,
        String autor,
        EstadoBorrador estado,
        int nodos,
        Instant actualizadoEn,
        Instant publicadoEn) {

    /** Resumen de un borrador. */
    public static ResumenSubgrafoDTO desde(BorradorSubgrafoEntity b) {
        return new ResumenSubgrafoDTO(b.getId(), b.getGrafoId(), b.getNombre(), b.getCategoria(),
                b.getEtiquetaOpcion(), b.getAutor(), b.getEstado(), b.getDocumento().nodos().size(),
                b.getActualizadoEn(), b.getPublicadoEn());
    }
}
