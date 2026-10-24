package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Pregunta existente a la que pasa a llevar una respuesta; {@code null} la vuelve un "+". */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pregunta existente destino de una respuesta; nodoId nulo quita el enlace.")
public class DestinoRequest {
    @Schema(description = "Id de la pregunta existente, o nulo para quitar el enlace")
    @Size(max = 100, message = "El id del nodo no es válido (demasiado largo).")
    private String nodoId;
}
