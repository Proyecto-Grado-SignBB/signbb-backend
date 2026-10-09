package org.signbb.backend.autoria.modelo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Documento de un borrador: lo que el médico armó en el lienzo. Se guarda tal
 * cual en la columna {@code documento} de {@code BorradorSubgrafoEntity}.
 *
 * @param inicio    id de la primera pregunta, o {@code null} si el lienzo
 *                  está vacío
 * @param secuencia último número usado para generar ids; los ids no se
 *                  reutilizan aunque se borre un nodo
 * @param nodos     nodos indexados por id, en el orden en que se crearon
 */
public record DocumentoLienzo(String inicio, int secuencia, Map<String, NodoLienzo> nodos) {

    /** Normaliza el mapa ausente a uno vacío y lo deja inmutable conservando el orden. */
    public DocumentoLienzo {
        nodos = Collections.unmodifiableMap(nodos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodos));
    }

    /** Lienzo vacío, el de un borrador recién creado. */
    public static DocumentoLienzo vacio() {
        return new DocumentoLienzo(null, 0, Map.of());
    }

    /** Nodo por id. */
    public Optional<NodoLienzo> nodo(String id) {
        return Optional.ofNullable(id == null ? null : nodos.get(id));
    }
}
