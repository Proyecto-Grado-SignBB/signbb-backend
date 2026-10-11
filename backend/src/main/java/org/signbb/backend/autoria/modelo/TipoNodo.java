package org.signbb.backend.autoria.modelo;

import io.swagger.v3.oas.annotations.media.Schema;

/** Clase de nodo del lienzo. */
@Schema(description = "Clase de nodo del lienzo.")
public enum TipoNodo {

    /** Pregunta para la cuidadora, con sus respuestas. */
    @Schema(description = "Pregunta para la cuidadora, con sus respuestas.")
    PREGUNTA,

    /** Cierre del recorrido: un desenlace y el mensaje para la cuidadora. */
    @Schema(description = "Cierre del recorrido: un desenlace y el mensaje para la cuidadora.")
    CONCLUSION
}
