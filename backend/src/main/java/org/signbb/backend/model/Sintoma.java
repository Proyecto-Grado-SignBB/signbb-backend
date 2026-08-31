package org.signbb.backend.model;

/**
 * Par (idNodo, respuesta) devuelto por {@link EstadoPaciente#sintomas()}.
 *
 * <p>Reemplaza la tupla {@code (str, str)} usada en la versión Python.
 */
public record Sintoma(String idNodo, String respuesta) {
}
