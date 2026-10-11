package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta del lienzo después de la cual se agrega un nodo. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta del lienzo después de la cual se agrega un nodo.")
public class DespuesDeRequest {
    @Schema(description = "Id del nodo que contiene la respuesta")
    private String nodoId;

    @Schema(description = "Id de la respuesta cuyo \"+\" se tocó")
    private String respuestaId;
}
