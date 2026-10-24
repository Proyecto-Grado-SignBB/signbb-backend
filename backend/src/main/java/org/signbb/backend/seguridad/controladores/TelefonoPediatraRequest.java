package org.signbb.backend.seguridad.controladores;

import org.signbb.backend.config.PatronesTexto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Teléfono del pediatra que guarda un padre.
 *
 * @param telefono dígitos, {@code +} y espacios, de 7 a 20 caracteres; vacío o
 *                 {@code null} borra el número guardado
 */
@Schema(description = "Teléfono del pediatra; vacío para borrarlo")
public record TelefonoPediatraRequest(
        @Schema(description = "Dígitos, + y espacios, de 7 a 20 caracteres", example = "+57 300 123 4567")
        @Pattern(regexp = "\\s*|" + PatronesTexto.TELEFONO,
                message = "Escribe un teléfono de 7 a 20 caracteres: solo números, + y espacios.")
        String telefono) {
}
