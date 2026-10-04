package org.signbb.backend.reglas.modelo;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;

/**
 * Literal de una {@link ReglaClinica}: la respuesta {@code respuesta} que se
 * espera en el nodo {@code idNodo}.
 *
 * <p>
 * Es la unidad mínima de una fórmula en forma normal disyuntiva (FND): se
 * agrupa en conjunciones ({@link Caso}) y las conjunciones se unen por
 * disyunción desde {@link ReglaClinica}. Se satisface cuando el
 * {@link EstadoPaciente} registró {@code respuesta} para {@code idNodo}. Con
 * selección múltiple el nodo puede tener varias respuestas marcadas y la
 * condición se cumple si la esperada está entre ellas, lo que permite combinar
 * con AND varias opciones de la misma pregunta.
 */
public record Condicion(String idNodo, String respuesta) {

    /** True si el estado registró la respuesta esperada en el nodo indicado. */
    public boolean evaluar(EstadoPaciente estado) {
        return estado.contieneRespuesta(idNodo, respuesta);
    }
}
