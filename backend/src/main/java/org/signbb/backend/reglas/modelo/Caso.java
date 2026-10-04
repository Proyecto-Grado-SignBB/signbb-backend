package org.signbb.backend.reglas.modelo;

import java.util.ArrayList;
import java.util.List;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;

/**
 * Conjunción de {@link Condicion}. Es uno de los casos de la forma normal
 * disyuntiva (FND) de una {@link ReglaClinica}.
 *
 * <p>
 * Se satisface cuando <em>todas</em> sus condiciones se satisfacen en el
 * {@link EstadoPaciente}; sus {@link Condicion} deben ser no nulas. La regla se
 * activa cuando al menos uno de sus {@link Caso} se satisface (OR de casos,
 * AND dentro de cada caso).
 */
public record Caso(List<Condicion> condiciones) {

    /** Valida y copia las condiciones (no nulas, no vacías). */
    public Caso {
        if (condiciones == null || condiciones.isEmpty()) {
            throw new IllegalArgumentException("un caso requiere al menos una condición");
        }
        for (Condicion c : condiciones) {
            if (c == null || c.idNodo() == null || c.respuesta() == null) {
                throw new IllegalArgumentException(
                        "cada condición debe ser (idNodo, respuesta) no nulos");
            }
        }
        condiciones = List.copyOf(condiciones);
    }

    /** True si TODAS las condiciones del caso se satisfacen en el estado. */
    public boolean evaluar(EstadoPaciente estado) {
        for (Condicion c : condiciones) {
            if (!c.evaluar(estado)) {
                return false;
            }
        }
        return true;
    }

    /** Copia en orden de las condiciones del caso. */
    public List<Condicion> condiciones() {
        return new ArrayList<>(condiciones);
    }
}
