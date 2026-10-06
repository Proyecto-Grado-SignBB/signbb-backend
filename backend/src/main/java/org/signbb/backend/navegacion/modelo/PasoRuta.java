package org.signbb.backend.navegacion.modelo;

/**
 * Un paso del recorrido real del cuidador: el nodo al que llegó (por id) y la
 * condición de la arista por la que llegó (cadena vacía para el primer nodo o
 * para un salto de regla "compuerta").
 *
 * <p>
 * A diferencia de {@link PasoCausal}, referencia el nodo por id y no por
 * objeto, de modo que se puede guardar en la sesión (Redis) y deshacer al
 * retroceder sin depender del grafo.
 *
 * @param idNodo    id del nodo alcanzado.
 * @param condicion opción elegida en la arista que llevó a ese nodo.
 */
public record PasoRuta(String idNodo, String condicion) {

    /** Valida el id y normaliza una condición nula a cadena vacía. */
    public PasoRuta {
        if (idNodo == null || idNodo.isEmpty()) {
            throw new IllegalArgumentException("idNodo debe ser una cadena no vacía");
        }
        condicion = condicion == null ? "" : condicion;
    }
}
