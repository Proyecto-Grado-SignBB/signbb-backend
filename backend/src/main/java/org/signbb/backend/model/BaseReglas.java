package org.signbb.backend.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TAD BaseReglas (Sección 4.2 del documento de diseño).
 *
 * <p>Colección de {@link ReglaClinica} indexada por idRegla. Estructura de
 * almacenamiento con una consulta clave: {@link #reglasActivadas(EstadoPaciente)},
 * que retorna las reglas cuyas condiciones se cumplen. No decide qué hacer con
 * esa lista — eso queda para el motor de inferencia, fuera de esta iteración.
 *
 * <p>Migrado desde {@code src/base_reglas.py}.
 */
public final class BaseReglas {

    private final Map<String, ReglaClinica> reglas = new LinkedHashMap<>();

    /** Agrega una regla nueva; falla si ya existe una con el mismo id. */
    public void agregarRegla(ReglaClinica regla) {
        if (regla == null) {
            throw new IllegalArgumentException("regla debe ser ReglaClinica");
        }
        if (reglas.containsKey(regla.getIdRegla())) {
            throw new IllegalArgumentException(
                    "ya existe una regla con id '" + regla.getIdRegla() + "'");
        }
        reglas.put(regla.getIdRegla(), regla);
    }

    /** Elimina la regla con el id dado; falla si no existe. */
    public void eliminarRegla(String idRegla) {
        if (!reglas.containsKey(idRegla)) {
            throw new IllegalArgumentException("no existe la regla '" + idRegla + "'");
        }
        reglas.remove(idRegla);
    }

    /** Retorna la regla con el id dado; falla si no existe. */
    public ReglaClinica obtenerRegla(String idRegla) {
        if (!reglas.containsKey(idRegla)) {
            throw new IllegalArgumentException("no existe la regla '" + idRegla + "'");
        }
        return reglas.get(idRegla);
    }

    /** Copia de todas las reglas en orden de inserción. */
    public List<ReglaClinica> todasLasReglas() {
        return new ArrayList<>(reglas.values());
    }

    /** Reglas cuyas condiciones se cumplen en el estado dado. */
    public List<ReglaClinica> reglasActivadas(EstadoPaciente estado) {
        List<ReglaClinica> activadas = new ArrayList<>();
        for (ReglaClinica r : reglas.values()) {
            if (r.condicionesCumplidas(estado)) {
                activadas.add(r);
            }
        }
        return activadas;
    }

    /** Número de reglas almacenadas. */
    public int size() {
        return reglas.size();
    }

    /** True si existe una regla con el id dado. */
    public boolean contains(String idRegla) {
        return reglas.containsKey(idRegla);
    }

    @Override
    public String toString() {
        return "BaseReglas(" + reglas.size() + " reglas)";
    }
}
