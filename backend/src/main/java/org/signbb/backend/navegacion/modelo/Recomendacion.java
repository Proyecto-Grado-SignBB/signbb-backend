package org.signbb.backend.navegacion.modelo;

import java.util.List;

/**
 * Una recomendación alcanzada durante el recorrido, con el camino real de
 * preguntas y respuestas que llevó hasta ella.
 *
 * @param nodo   nodo terminal de tipo {@code recomendacion}.
 * @param camino recorrido real hasta ese nodo (explicación trazable).
 */
public record Recomendacion(Nodo nodo, List<PasoCausal> camino) {

    /** Guarda una copia inmutable del camino. */
    public Recomendacion {
        if (nodo == null) {
            throw new IllegalArgumentException("nodo no puede ser nulo");
        }
        camino = camino == null ? List.of() : List.copyOf(camino);
    }
}
