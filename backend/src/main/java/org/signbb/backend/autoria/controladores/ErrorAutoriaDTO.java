package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Aviso;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de un error de autoría.
 *
 * @param mensaje texto para el médico
 * @param avisos  avisos que lo causan (vacía si no aplica)
 */
@Schema(description = "Cuerpo de un error de autoría (422 o 409).")
public record ErrorAutoriaDTO(
        @Schema(description = "Texto para el médico") String mensaje,
        @Schema(description = "Avisos que lo causan; vacía si no aplica") List<Aviso> avisos) {
}
