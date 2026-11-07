package org.signbb.backend.autoria.controladores;

import org.signbb.backend.autoria.modelo.Gravedad;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.signbb.backend.config.PatronesTexto;

/** Respuesta de una pregunta; {@code id} solo al editar una que ya existe. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de una pregunta.")
public class OpcionRequest {
    @Schema(description = "Id de la respuesta; solo al editar una que ya existe")
    @Size(max = 100, message = "El id de la respuesta no es válido (demasiado largo).")
    private String id;

    @Schema(description = "Texto de la opción")
    @Size(max = 60, message = "El texto de una respuesta no puede pasar de 60 caracteres.")
    @Pattern(regexp = PatronesTexto.UNA_LINEA, message = "El texto de una respuesta no puede tener saltos de línea.")
    private String texto;

    @Schema(description = "Gravedad asociada a la opción")
    private Gravedad gravedad;
}
