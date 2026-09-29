package org.signbb.backend.reglas.modelo;

import java.util.List;
import java.util.Optional;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;

/**
 * Condición de una {@link ReglaClinica}: la respuesta {@code respuesta} que se
 * espera en el nodo {@code idNodo}.
 *
 * <p>
 * Reemplaza la tupla {@code (id_nodo, respuesta)} usada en la versión Python.
 *
 * <p>
 * Es la hoja del árbol de {@link ExpresionCondicion}: se satisface cuando el
 * {@link EstadoPaciente} registró exactamente {@code respuesta} para
 * {@code idNodo}.
 */
public record Condicion(String idNodo, String respuesta) implements ExpresionCondicion {

    @Override
    public boolean evaluar(EstadoPaciente estado) {
        Optional<String> actual = estado.getRespuesta(idNodo);
        return actual.isPresent() && actual.get().equals(respuesta);
    }

    @Override
    public void recolectarHojas(List<Condicion> acc) {
        acc.add(this);
    }
}
