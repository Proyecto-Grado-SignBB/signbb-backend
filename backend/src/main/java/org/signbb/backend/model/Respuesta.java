package org.signbb.backend.model;

import java.util.Objects;

/**
 * TAD Respuesta (arista) del Grafo Clínico (Sección 3 del documento de diseño).
 *
 * <p>Representa una arista dirigida entre un nodo origen y un nodo destino,
 * condicionada por una opción de respuesta. Dos aristas son iguales si
 * coinciden en (idOrigen, idDestino, condición).
 *
 * <p>Migrado desde {@code src/respuesta.py}.
 */
public final class Respuesta {

    private final Nodo origen;
    private final Nodo destino;
    private final String condicion;

    /** Crea la arista activada por la respuesta {@code condicion}. */
    public Respuesta(Nodo origen, Nodo destino, String condicion) {
        if (origen == null) {
            throw new IllegalArgumentException("origen debe ser Nodo");
        }
        if (destino == null) {
            throw new IllegalArgumentException("destino debe ser Nodo");
        }
        if (condicion == null || condicion.isEmpty()) {
            throw new IllegalArgumentException("condicion debe ser una cadena no vacía");
        }
        this.origen = origen;
        this.destino = destino;
        this.condicion = condicion;
    }

    /** Nodo del que sale la arista. */
    public Nodo getOrigen() {
        return origen;
    }

    /** Nodo al que llega la arista. */
    public Nodo getDestino() {
        return destino;
    }

    /** Respuesta que activa la arista. */
    public String getCondicion() {
        return condicion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Respuesta)) {
            return false;
        }
        Respuesta otra = (Respuesta) o;
        return origen.getId().equals(otra.origen.getId())
                && destino.getId().equals(otra.destino.getId())
                && condicion.equals(otra.condicion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origen.getId(), destino.getId(), condicion);
    }

    @Override
    public String toString() {
        return "Respuesta(" + origen.getId() + " --[" + condicion + "]--> "
                + destino.getId() + ")";
    }
}
