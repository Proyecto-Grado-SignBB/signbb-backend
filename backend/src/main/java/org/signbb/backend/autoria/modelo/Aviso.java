package org.signbb.backend.autoria.modelo;

/**
 * Aviso sobre el lienzo, escrito para el médico.
 *
 * @param codigo      clase de aviso
 * @param bloqueante  si impide publicar
 * @param mensaje     texto en lenguaje humano
 * @param nodoId      nodo al que se refiere, o {@code null}
 * @param respuestaId respuesta a la que se refiere, o {@code null}
 */
public record Aviso(Codigo codigo, boolean bloqueante, String mensaje, String nodoId, String respuestaId) {

    /** Clases de aviso. Todas bloquean la publicación salvo {@code NODO_SUELTO}. */
    public enum Codigo {
        /** El lienzo no tiene primera pregunta. */
        SIN_INICIO,
        /** Una pregunta, respuesta o conclusión sin texto. */
        TEXTO_VACIO,
        /** Una pregunta con menos de dos respuestas. */
        POCAS_RESPUESTAS,
        /** Dos respuestas con el mismo texto en una pregunta. */
        RESPUESTA_REPETIDA,
        /** Una respuesta que todavía es un "+" (o apunta a un nodo que ya no existe). */
        RESPUESTA_SIN_CAMINO,
        /** Una conclusión sin desenlace. */
        SIN_DESENLACE,
        /** Un camino que vuelve a una pregunta anterior. */
        CICLO,
        /** Un nodo al que no llega ninguna respuesta; no se publica. */
        NODO_SUELTO
    }
}
