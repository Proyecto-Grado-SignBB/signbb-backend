package org.signbb.backend.reglas.modelo;

/**
 * Condición de una {@link ReglaClinica}: la respuesta {@code respuesta} que se
 * espera en el nodo {@code idNodo}.
 *
 * <p>
 * Reemplaza la tupla {@code (id_nodo, respuesta)} usada en la versión Python.
 */
public record Condicion(String idNodo, String respuesta) {
}
