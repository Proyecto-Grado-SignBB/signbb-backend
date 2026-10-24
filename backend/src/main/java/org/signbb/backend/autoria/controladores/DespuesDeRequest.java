package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Respuesta del lienzo después de la cual se agrega un nodo. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta del lienzo después de la cual se agrega un nodo.")
public class DespuesDeRequest {
    @Schema(description = "Id del nodo que contiene la respuesta")
    @Size(max = 100, message = "El id del nodo no es válido (demasiado largo).")
    private String nodoId;

    @Schema(description = "Id de la respuesta cuyo \"+\" se tocó")
    @Size(max = 100, message = "El id de la respuesta no es válido (demasiado largo).")
    private String respuestaId;
}
