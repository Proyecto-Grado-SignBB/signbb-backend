package org.signbb.backend.autoria.modelo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aviso sobre el lienzo, escrito para el médico.
 *
 * @param codigo      clase de aviso
 * @param bloqueante  si impide publicar
 * @param mensaje     texto en lenguaje humano
 * @param nodoId      nodo al que se refiere, o {@code null}
 * @param respuestaId respuesta a la que se refiere, o {@code null}
 */
@Schema(description = "Aviso sobre el lienzo, escrito para el médico.")
public record Aviso(
        @Schema(description = "Clase de aviso") Codigo codigo,
        @Schema(description = "Si impide publicar") boolean bloqueante,
        @Schema(description = "Texto en lenguaje humano") String mensaje,
        @Schema(description = "Nodo al que se refiere, o nulo") String nodoId,
        @Schema(description = "Respuesta a la que se refiere, o nula") String respuestaId) {

    /**
     * Clases de aviso del lienzo y de las reglas. Todos los avisos actuales
     * bloquean la publicación.
     */
    @Schema(description = "Clases de aviso del lienzo y de las reglas.")
    public enum Codigo {
        /** El lienzo no tiene primera pregunta. */
        @Schema(description = "El lienzo no tiene primera pregunta.")
        SIN_INICIO,
        /** Una pregunta, respuesta o conclusión sin texto. */
        @Schema(description = "Una pregunta, respuesta o conclusión sin texto.")
        TEXTO_VACIO,
        /** Una pregunta con menos de dos respuestas. */
        @Schema(description = "Una pregunta con menos de dos respuestas.")
        POCAS_RESPUESTAS,
        /** Dos respuestas con el mismo texto en una pregunta. */
        @Schema(description = "Dos respuestas con el mismo texto en una pregunta.")
        RESPUESTA_REPETIDA,
        /** Una respuesta que todavía es un "+" (o apunta a un nodo que ya no existe). */
        @Schema(description = "Una respuesta que todavía es un \"+\" (o apunta a un nodo que ya no existe).")
        RESPUESTA_SIN_CAMINO,
        /** Una conclusión sin desenlace. */
        @Schema(description = "Una conclusión sin desenlace.")
        SIN_DESENLACE,
        /** Un camino que vuelve a una pregunta anterior. */
        @Schema(description = "Un camino que vuelve a una pregunta anterior.")
        CICLO,
        /** Un nodo al que no llega ninguna respuesta; bloquea hasta conectarlo. */
        @Schema(description = "Un nodo al que no llega ninguna respuesta; bloquea hasta conectarlo.")
        NODO_SUELTO,
        /** Una regla sin nombre. */
        @Schema(description = "Una regla sin nombre.")
        REGLA_SIN_NOMBRE,
        /** Una regla sin condiciones (FND vacía). */
        @Schema(description = "Una regla sin condiciones.")
        REGLA_SIN_CONDICIONES,
        /** Una regla sin nodo destino. */
        @Schema(description = "Una regla sin nodo destino.")
        REGLA_SIN_DESTINO,
        /** Una regla con nivel NORMAL, que no es una alerta válida. */
        @Schema(description = "Una regla con nivel NORMAL, que no es una alerta válida.")
        REGLA_NIVEL_NORMAL,
        /** Una condición que referencia un nodo o una respuesta inexistente. */
        @Schema(description = "Una condición que referencia un nodo o una respuesta inexistente.")
        REGLA_CONDICION_INVALIDA,
        /** El nodo destino de una regla no existe en el grafo. */
        @Schema(description = "El nodo destino de una regla no existe en el grafo.")
        REGLA_DESTINO_INEXISTENTE
    }
}
