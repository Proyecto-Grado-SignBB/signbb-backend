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
 * @param imagenes         nombres de las imágenes de apoyo de la pregunta,
 *                         máximo 3, en el orden en que se muestran (vacía en
 *                         una conclusión)
 */
public record NodoLienzo(
        String id,
        TipoNodo tipo,
        String texto,
        boolean variasRespuestas,
        Desenlace desenlace,
        List<RespuestaLienzo> respuestas,
        List<String> imagenes) {

    /**
     * Normaliza las listas ausentes a una lista vacía inmutable. Los lienzos
     * guardados antes de las imágenes no tienen {@code imagenes}.
     */
    public NodoLienzo {
        respuestas = respuestas == null ? List.of() : List.copyOf(respuestas);
        imagenes = imagenes == null ? List.of() : List.copyOf(imagenes);
    }

    /** Nodo sin imágenes. */
    public NodoLienzo(String id, TipoNodo tipo, String texto, boolean variasRespuestas, Desenlace desenlace,
            List<RespuestaLienzo> respuestas) {
        this(id, tipo, texto, variasRespuestas, desenlace, respuestas, List.of());
    }

    /** True si es una pregunta. */
    public boolean esPregunta() {
        return tipo == TipoNodo.PREGUNTA;
    }

    /** Copia con otras respuestas. */
    public NodoLienzo conRespuestas(List<RespuestaLienzo> nuevas) {
        return new NodoLienzo(id, tipo, texto, variasRespuestas, desenlace, nuevas, imagenes);
    }
}
