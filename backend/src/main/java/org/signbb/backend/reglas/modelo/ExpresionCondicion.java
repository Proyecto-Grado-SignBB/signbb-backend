package org.signbb.backend.reglas.modelo;

import java.util.ArrayList;
import java.util.List;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;

/**
 * Expresión lógica de condiciones de una {@link ReglaClinica} (patrón
 * intérprete / composite).
 *
 * <p>
 * Permite combinar condiciones con {@code AND} ({@link Y}) y {@code OR}
 * ({@link O}) y anidarlas a cualquier profundidad; la hoja del árbol es una
 * {@link Condicion} (un par {@code idNodo → respuesta esperada}). La regla se
 * activa cuando {@link #evaluar(EstadoPaciente)} sobre la expresión raíz es
 * {@code true}.
 *
 * <p>
 * Antes las condiciones eran una lista plana unida siempre por AND; esta
 * abstracción generaliza ese caso (una lista plana equivale a un {@link Y} de
 * sus hojas) sin romper la semántica previa.
 */
public sealed interface ExpresionCondicion
        permits Condicion, ExpresionCondicion.Y, ExpresionCondicion.O {

    /** True si la expresión se satisface en el estado dado. */
    boolean evaluar(EstadoPaciente estado);

    /** Acumula en {@code acc}, en orden, las condiciones hoja del árbol. */
    void recolectarHojas(List<Condicion> acc);

    /** Lista, en orden, de las condiciones hoja del árbol. */
    default List<Condicion> hojas() {
        List<Condicion> acc = new ArrayList<>();
        recolectarHojas(acc);
        return acc;
    }

    /** Conjunción: se satisface cuando TODOS los operandos se satisfacen. */
    record Y(List<ExpresionCondicion> operandos) implements ExpresionCondicion {

        /** Valida y copia los operandos (no nulos, no vacíos). */
        public Y {
            if (operandos == null || operandos.isEmpty()) {
                throw new IllegalArgumentException("Y (AND) requiere al menos un operando");
            }
            operandos = List.copyOf(operandos);
        }

        @Override
        public boolean evaluar(EstadoPaciente estado) {
            for (ExpresionCondicion e : operandos) {
                if (!e.evaluar(estado)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void recolectarHojas(List<Condicion> acc) {
            for (ExpresionCondicion e : operandos) {
                e.recolectarHojas(acc);
            }
        }
    }

    /** Disyunción: se satisface cuando ALGÚN operando se satisface. */
    record O(List<ExpresionCondicion> operandos) implements ExpresionCondicion {

        /** Valida y copia los operandos (no nulos, no vacíos). */
        public O {
            if (operandos == null || operandos.isEmpty()) {
                throw new IllegalArgumentException("O (OR) requiere al menos un operando");
            }
            operandos = List.copyOf(operandos);
        }

        @Override
        public boolean evaluar(EstadoPaciente estado) {
            for (ExpresionCondicion e : operandos) {
                if (e.evaluar(estado)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void recolectarHojas(List<Condicion> acc) {
            for (ExpresionCondicion e : operandos) {
                e.recolectarHojas(acc);
            }
        }
    }
}
