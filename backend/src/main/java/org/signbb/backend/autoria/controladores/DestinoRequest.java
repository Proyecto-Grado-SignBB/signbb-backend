package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

/** Pregunta existente a la que pasa a llevar una respuesta; {@code null} la vuelve un "+". */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pregunta existente destino de una respuesta; nodoId nulo quita el enlace.")
public class DestinoRequest {
    @Schema(description = "Id de la pregunta existente, o nulo para quitar el enlace")
    private String nodoId;
}
