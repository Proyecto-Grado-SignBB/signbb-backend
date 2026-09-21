package org.signbb.backend.navegacion.datos.serializacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Convierte el JSON del grafo clínico ({@code graph}) en el TAD
 * {@link GrafoClinico}.
 */
public class GrafoClinicoParser {

    /** Carga el grafo clínico desde el JSON con la clave {@code graph}. */
    public static GrafoClinico parseJsonToGrafoClinico(String jsonFile) {
        GrafoClinico grafo = new GrafoClinico();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonFile);

        JsonNode graph = root.get("graph");
        JsonNode nodes = graph.get("nodes");
        JsonNode edges = graph.get("edges");

        Map<String, Nodo> nodosPorId = new LinkedHashMap<>();

        for (Map.Entry<String, JsonNode> e : nodes.properties()) {
            String id = e.getKey();
            JsonNode info = e.getValue();
            JsonNode meta = info.path("metadata");
            Nodo nodo = new Nodo(
                    id,
                    normalizarTipo(meta.path("tipo").asText()),
                    info.path("label").asText(),
                    meta.path("peso").asInt(),
                    meta.path("archivos").asBoolean(),
                    buildSrc(meta.path("src")),
                    buildTipoInteraccion(meta.path("tipoInteraccion")));
            nodosPorId.put(id, nodo);
            grafo.agregarNodo(nodo);
        }

        for (JsonNode edge : edges) {
            Nodo origen = nodosPorId.get(edge.path("source").asText());
            Nodo destino = nodosPorId.get(edge.path("target").asText());
            grafo.conectarRespuestas(origen, destino, edge.path("relation").asText());
        }

        return grafo;
    }

    /** El JSON usa "inicio" y "consulta médica"; el TAD espera otros valores. */
    private static String normalizarTipo(String tipo) {
        return switch (tipo) {
            case "inicio" -> "pregunta";
            case "consulta médica" -> "consulta_medica";
            default -> tipo;
        };
    }

    /**
     * Mapea el arreglo JSON {@code metadata.src} a una lista de rutas de imagen.
     */
    private static List<String> buildSrc(JsonNode srcJson) {
        List<String> src = new ArrayList<>();
        if (srcJson.isArray()) {
            for (JsonNode s : srcJson) {
                src.add(s.asText());
            }
        }
        return src;
    }

    /**
     * NUEVO: mapea {@code metadata.tipoInteraccion} (opcional) a un String o
     * {@code null} si el nodo no lo especifica. La validación de que el valor
     * sea uno de los admitidos ({@code Nodo.TIPOS_INTERACCION_VALIDOS}) la
     * hace el constructor de {@link Nodo}.
     */
    private static String buildTipoInteraccion(JsonNode tipoInteraccionJson) {
        return (tipoInteraccionJson.isMissingNode() || tipoInteraccionJson.isNull())
                ? null
                : tipoInteraccionJson.asText();
    }
}