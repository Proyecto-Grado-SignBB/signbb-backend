package org.signbb.backend.navegacion.datos.serializacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Documento JSON de un grafo clínico: el formato de intercambio de
 * {@code static/ejemplo_subgrafo_piel_ictericia.json}, usado además como
 * formato persistido en la columna {@code estructura} de
 * {@code GrafoClinicoEntity}.
 *
 * <p>
 * Hay dos clases de documento con el mismo formato: el <b>grafo raíz</b> del
 * sistema (pregunta inicial, preguntas puente por categoría y nodos
 * {@code referencia}) y los <b>subgrafos</b> que crea el médico para una
 * categoría. {@link CompositorGrafoClinico} los aplana en un único documento
 * sin referencias antes de construir el TAD.
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

        /**
         * Sección {@code graph} del documento. {@code preguntaInicial} es el nodo
         * donde arranca el recorrido; {@code nodoInicial} es su nombre en el
         * formato previo y se conserva solo para leer documentos antiguos.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Grafo(
                        String id,
                        String label,
                        Boolean directed,
                        List<String> category,
                        String preguntaInicial,
                        String nodoInicial,
                        Map<String, Object> metadata,
                        Map<String, NodoJson> nodes,
                        List<Arista> edges) {
        }

        /**
         * Nodo del grafo: {@code label} es el contenido y el resto vive en metadata.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record NodoJson(String label, MetadataNodo metadata) {
        }

        /**
         * Metadatos usados para reconstruir el TAD {@code Nodo}. {@code tipo} es
         * {@code pregunta}, {@code terminal} o {@code referencia}; según el tipo
         * aplican {@code tipoInteraccion} (pregunta), {@code desenlace}
         * (terminal: {@code recomendacion}, {@code consulta_medica} o
         * {@code urgencia}) o {@code grafoId} (referencia). {@code peso} va de 1 a
         * 3. {@code peso} y {@code archivos} usan wrappers para tolerar campos
         * ausentes en el JSON (Jackson 3 rechaza null sobre primitivas por
         * defecto). {@code tipoInteraccion} ausente/null significa que
         * {@code GrafoClinico.tipoInteraccion} lo infiere de las opciones del nodo.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MetadataNodo(
                        String tipo,
                        String tipoInteraccion,
                        String desenlace,
                        String grafoId,
                        Integer peso,
                        Boolean archivos,
                        List<String> src) {
        }

        /** Arista: referencia los nodos por id, nunca embebe el objeto {@code Nodo}. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Arista(
                        String source,
                        String relation,
                        String target,
                        Boolean directed) {
        }
}
