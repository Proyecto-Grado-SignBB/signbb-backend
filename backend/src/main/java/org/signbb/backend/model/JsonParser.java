package org.signbb.backend.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Convierte los JSON del grafo clínico ({@code graph}) y de la base de reglas
 * ({@code rules}) en los TAD del modelo.
 */
public class JsonParser {

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
                    buildSrc(meta.path("src")));
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
     * Carga la base de reglas desde el JSON con la clave {@code rules},
     * resolviendo cada {@code nodoDestino} contra los nodos del grafo.
     */
    public static BaseReglas parseJsonToBaseReglas(String jsonFile, GrafoClinico grafo) {
        BaseReglas base = new BaseReglas();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode baseJson = mapper.readTree(jsonFile);

        JsonNode reglasJson = baseJson.get("rules");

        for (Map.Entry<String, JsonNode> e : reglasJson.properties()) {
            String id = e.getKey();
            JsonNode info = e.getValue();
            NivelAlerta nivel = NivelAlerta.valueOf(info.path("nivel").asText());
            if (nivel == NivelAlerta.NORMAL) {
                throw new IllegalArgumentException(
                        "la regla '" + id + "' no puede tener nivel NORMAL");
            }
            ReglaClinica regla = new ReglaClinica(
                    id,
                    info.path("nombre").asText(),
                    buildCondiciones(info.path("condiciones")),
                    buildDestino(info.path("nodoDestino").asText(), grafo),
                    nivel);
            base.agregarRegla(regla);
        }
        return base;
    }

    /** Mapea el arreglo JSON de condiciones a una lista de {@link Condicion}. */
    private static List<Condicion> buildCondiciones(JsonNode condicionesJson) {
        List<Condicion> condiciones = new ArrayList<>();
        for (JsonNode condicion : condicionesJson) {
            condiciones.add(new Condicion(
                    condicion.path("idNodo").asText(),
                    condicion.path("respuesta").asText()));
        }
        return condiciones;
    }

    /** Resuelve el {@code nodoDestino} de una regla contra los nodos del grafo. */
    private static Nodo buildDestino(String idNodo, GrafoClinico grafo) {
        return grafo.obtenerNodoPorId(idNodo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "nodoDestino '" + idNodo + "' no existe en el grafo"));
    }
}