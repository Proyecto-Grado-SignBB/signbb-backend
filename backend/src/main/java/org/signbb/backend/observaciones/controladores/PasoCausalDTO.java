package org.signbb.backend.observaciones.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un paso del camino causal hacia el desenlace: el {@code nodoId} alcanzado,
 * la {@code condicion} de la arista por la que se llegó (vacía para el nodo
 * inicial) y el {@code contenido} del nodo.
 *
 * <p>
 * MODIFICADO (2.6): se agrega {@code contenido} para que el front muestre el
 * camino en palabras ("¿Por qué este resultado?"): la {@code condicion} de un
 * paso es la respuesta que se dio a la pregunta del paso anterior.
 */
@Schema(description = "Un paso del camino causal hacia el desenlace.")
public record PasoCausalDTO(
        @Schema(description = "Id del nodo alcanzado") String nodoId,
        @Schema(description = "Condición de la arista por la que se llegó (la respuesta a la pregunta "
                + "del paso anterior); vacía para el nodo inicial")
        String condicion,
        @Schema(description = "Texto del nodo alcanzado: la pregunta, o el mensaje si es el desenlace")
        String contenido) {
}
