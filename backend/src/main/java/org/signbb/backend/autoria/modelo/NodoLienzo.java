package org.signbb.backend.autoria.modelo;

import java.util.List;

/**
 * Nodo del lienzo.
 *
 * @param id               id generado por el servidor ({@code pregunta_N} o
 *                         {@code conclusion_N})
 * @param tipo             pregunta o conclusión
 * @param texto            enunciado de la pregunta o, en una conclusión, el
 *                         mensaje para la cuidadora
 * @param variasRespuestas si la cuidadora puede marcar varias respuestas
 *                         (solo preguntas)
 * @param desenlace        desenlace de la conclusión ({@code null} en una
 *                         pregunta)
 * @param respuestas       respuestas de la pregunta, en el orden en que se
 *                         muestran (vacía en una conclusión)
 */
public record NodoLienzo(
        String id,
        TipoNodo tipo,
        String texto,
        boolean variasRespuestas,
        Desenlace desenlace,
        List<RespuestaLienzo> respuestas) {

    /** Normaliza la lista de respuestas ausente a una lista vacía inmutable. */
    public NodoLienzo {
        respuestas = respuestas == null ? List.of() : List.copyOf(respuestas);
    }

    /** True si es una pregunta. */
    public boolean esPregunta() {
        return tipo == TipoNodo.PREGUNTA;
    }

    /** Copia con otras respuestas. */
    public NodoLienzo conRespuestas(List<RespuestaLienzo> nuevas) {
        return new NodoLienzo(id, tipo, texto, variasRespuestas, desenlace, nuevas);
    }
}
