package org.signbb.backend.reglas.datos.serializacion;

import java.util.ArrayList;
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
 * Convierte el JSON de la base de reglas ({@code rules}) en el TAD
 * {@link BaseReglas}.
 */
public class BaseReglasParser {

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
