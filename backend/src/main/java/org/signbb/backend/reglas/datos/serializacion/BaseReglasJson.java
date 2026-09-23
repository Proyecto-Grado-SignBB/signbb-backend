package org.signbb.backend.reglas.datos.serializacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

import org.signbb.backend.reglas.modelo.Condicion;

/**
 * Documento JSON de una base de reglas: el mismo formato de intercambio de
 * {@code static/ejemplo_reglas.json}, usado además como formato persistido en
 * la columna {@code estructura} de {@code BaseReglasEntity}.
 *
 * <p>
 * Cada regla referencia su {@code nodoDestino} por id (nunca embebe el nodo):
 * el destino se resuelve contra el grafo clínico correspondiente al
 * reconstruir el TAD, fallando de inmediato si la referencia queda colgando.
 * El {@code nivel} se mantiene como cadena para preservar el contrato de los
 * parsers (valores inválidos lanzan {@link IllegalArgumentException}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaseReglasJson(Map<String, ReglaJson> rules) {

    /** Regla clínica en formato serializable. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReglaJson(
            String nombre,
            List<Condicion> condiciones,
            String nodoDestino,
            String nivel,
            Map<String, Object> metadata) {
    }
}
