package org.signbb.backend.model;

/**
 * Paso dentro del camino causal devuelto por
 * {@link GrafoClinico#obtenerCaminoCausal(Nodo)}: el {@code nodo} alcanzado y la
 * {@code condicion} de la arista por la que se llegó (cadena vacía para el nodo
 * inicial).
 *
 * <p>Reemplaza la tupla {@code (Nodo, str)} usada en la versión Python.
 */
public record PasoCausal(Nodo nodo, String condicion) {
}
