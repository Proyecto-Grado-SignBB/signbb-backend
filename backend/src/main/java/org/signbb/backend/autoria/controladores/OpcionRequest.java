package org.signbb.backend.autoria.controladores;

import org.signbb.backend.autoria.modelo.Gravedad;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de una pregunta; {@code id} solo al editar una que ya existe. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de una pregunta.")
public class OpcionRequest {
    @Schema(description = "Id de la respuesta; solo al editar una que ya existe")
    private String id;

    @Schema(description = "Texto de la opción")
    private String texto;

    @Schema(description = "Gravedad asociada a la opción")
    private Gravedad gravedad;
}
