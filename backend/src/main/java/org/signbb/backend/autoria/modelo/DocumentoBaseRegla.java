package org.signbb.backend.autoria.modelo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Documento de un borrador de base de reglas: las reglas que el médico
 * definió. Se guarda tal cual en la columna {@code documento} de
 * {@code BorradorBaseReglaEntity}.
 *
 * @param secuencia último número usado para generar ids; los ids no se
 *                  reutilizan aunque se borre una regla
 * @param reglas    reglas indexadas por id, en el orden en que se crearon
 */
public record DocumentoBaseRegla(int secuencia, Map<String, BorradorRegla> reglas) {

    /** Normaliza el mapa ausente a uno vacío y lo deja inmutable conservando el orden. */
    public DocumentoBaseRegla {
        reglas = Collections.unmodifiableMap(reglas == null ? new LinkedHashMap<>() : new LinkedHashMap<>(reglas));
    }

    /** Base vacía, la de un borrador recién creado. */
    public static DocumentoBaseRegla vacio() {
        return new DocumentoBaseRegla(0, Map.of());
    }

    /** Regla por id. */
    public Optional<BorradorRegla> regla(String id) {
        return Optional.ofNullable(id == null ? null : reglas.get(id));
    }
}
