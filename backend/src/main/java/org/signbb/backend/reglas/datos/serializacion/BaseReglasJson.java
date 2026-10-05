package org.signbb.backend.reglas.datos.serializacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Documento JSON de una base de reglas: el mismo formato de intercambio de
 * {@code static/ejemplo_reglas_piel_ictericia.json}, usado además como formato
 * persistido en la columna {@code estructura} de {@code BaseReglasEntity}.
 *
 * <p>
 * {@code grafoId} indica a qué grafo pertenece la base. Las reglas de un
 * subgrafo usan los ids locales de sus nodos y {@code CompositorGrafoClinico}
 * los prefija al aplanar; las reglas del grafo raíz usan los ids ya prefijados
 * y permiten combinar signos de varias categorías.
 *
 * <p>
 * Cada regla referencia su {@code nodoDestino} por id (nunca embebe el nodo):
 * el destino se resuelve contra el grafo clínico correspondiente al
 * reconstruir el TAD, fallando de inmediato si la referencia queda colgando.
 * El {@code nivel} se mantiene como cadena para preservar el contrato de los
 * parsers (valores inválidos lanzan {@link IllegalArgumentException}).
 *
 * <p>
 * {@code condiciones} se conserva como {@link JsonNode} porque la premisa se
 * expresa en forma normal disyuntiva (FND): un arreglo de casos, donde cada
 * caso es un arreglo de condiciones. El formato plano (arreglo de condiciones)
 * se acepta como un único caso por compatibilidad; la expresión se interpreta
 * y se reconstruye en {@link BaseReglasMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaseReglasJson(String grafoId, Map<String, ReglaJson> rules) {

    /** Regla clínica en formato serializable. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReglaJson(
            String nombre,
            JsonNode condiciones,
            String nodoDestino,
            String nivel,
            Map<String, Object> metadata) {
    }
}
