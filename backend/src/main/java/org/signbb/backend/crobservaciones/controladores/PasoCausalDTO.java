package org.signbb.backend.crobservaciones.controladores;

/**
 * Un paso del camino causal hacia el desenlace: el {@code nodoId} alcanzado y
 * la {@code condicion} de la arista por la que se llegó (vacía para el nodo
 * inicial).
 */
public record PasoCausalDTO(String nodoId, String condicion) {
}
