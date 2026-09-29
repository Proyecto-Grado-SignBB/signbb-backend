package org.signbb.backend.reglas.modelo;

import java.util.List;

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
 * {@link EstadoPaciente} registró {@code respuesta} para {@code idNodo}. Con
 * selección múltiple el nodo puede tener varias respuestas marcadas y la
 * condición se cumple si la esperada está entre ellas, lo que permite combinar
 * con AND varias opciones de la misma pregunta.
 */
public record Condicion(String idNodo, String respuesta) implements ExpresionCondicion {

    @Override
    public boolean evaluar(EstadoPaciente estado) {
        return estado.contieneRespuesta(idNodo, respuesta);
    }

    @Override
    public void recolectarHojas(List<Condicion> acc) {
        acc.add(this);
    }
}
