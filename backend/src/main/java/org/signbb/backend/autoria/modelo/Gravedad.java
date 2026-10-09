package org.signbb.backend.autoria.modelo;

/**
 * Gravedad que el médico le pone a cada respuesta, el semáforo del lienzo
 * (verde, amarillo y rojo). Al compilar, el {@code peso} de un nodo es la
 * mayor gravedad de las respuestas que llegan a él.
 */
public enum Gravedad {

    /** Verde. */
    LEVE(1),

    /** Amarillo. */
    MODERADA(2),

    /** Rojo. */
    GRAVE(3);

    private final int peso;

    /** @param peso peso del grafo clínico (1–3) */
    Gravedad(int peso) {
        this.peso = peso;
    }

    /** Peso del grafo clínico (escala 1–3) que corresponde a esta gravedad. */
    public int peso() {
        return peso;
    }
}
