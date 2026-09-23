package org.signbb.backend.navegacion.datos.serializacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Documento JSON de un grafo clínico: el mismo formato de intercambio de
 * {@code static/ejemplo_grafo.json}, usado además como formato persistido en
 * la columna {@code estructura} de {@code GrafoClinicoEntity}.
 *
 * <p>
 * Los nodos se almacenan una sola vez (indexados por id) y las aristas
 * referencian nodos por id, de modo que la estructura interna queda
 * inspeccionable (por SQL sobre el jsonb) y no se duplican datos entre
 * elementos que apunten a los mismos nodos. El estado de navegación
 * ({@code bloqueado}) y los umbrales de peso (configuración de uso del TAD)
 * no forman parte del documento.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrafoClinicoJson(Grafo graph) {

    /** Sección {@code graph} del documento. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Grafo(
            String label,
            Map<String, Object> metadata,
            String nodoInicial,
            Map<String, NodoJson> nodes,
            List<Arista> edges) {
    }

    /** Nodo del grafo: {@code label} es el contenido y el resto vive en metadata. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodoJson(String label, MetadataNodo metadata) {
    }

    /**
     * Metadatos usados para reconstruir el TAD {@code Nodo}. {@code peso} y
     * {@code archivos} usan wrappers para tolerar campos ausentes en el JSON
     * (Jackson 3 rechaza null sobre primitivas por defecto).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetadataNodo(
            String tipo,
            Integer peso,
            Boolean archivos,
            List<String> src,
            List<String> categorias) {
    }

    /** Arista: referencia los nodos por id, nunca embebe el objeto {@code Nodo}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Arista(
            String source,
            String relation,
            String target,
            Map<String, Object> metadata) {
    }
}
