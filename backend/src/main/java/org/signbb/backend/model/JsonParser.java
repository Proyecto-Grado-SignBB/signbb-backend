package org.signbb.backend.model;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JsonParser {
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
                    meta.path("peso").asInt());
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
}
