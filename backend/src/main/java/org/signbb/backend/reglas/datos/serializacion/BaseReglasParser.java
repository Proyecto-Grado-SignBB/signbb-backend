package org.signbb.backend.reglas.datos.serializacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.ExpresionCondicion;
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
                    buildExpresion(info.path("condiciones")),
                    buildDestino(info.path("nodoDestino").asText(), grafo),
                    nivel);
            base.agregarRegla(regla);
        }
        return base;
    }

    /**
     * Construye la {@link ExpresionCondicion} de una regla de forma recursiva:
     * <ul>
     * <li>un arreglo JSON se interpreta como {@code AND} implícito de sus
     * elementos (compatibilidad con el formato plano previo);</li>
     * <li>un objeto con clave {@code "and"} o {@code "or"} produce
     * {@link ExpresionCondicion.Y} o {@link ExpresionCondicion.O} de la lista
     * anidada;</li>
     * <li>un objeto con {@code idNodo}/{@code respuesta} es una hoja
     * {@link Condicion}.</li>
     * </ul>
     */
    private static ExpresionCondicion buildExpresion(JsonNode nodo) {
        if (nodo.isArray()) {
            return new ExpresionCondicion.Y(buildOperandos(nodo));
        }
        if (nodo.has("and")) {
            return new ExpresionCondicion.Y(buildOperandos(nodo.get("and")));
        }
        if (nodo.has("or")) {
            return new ExpresionCondicion.O(buildOperandos(nodo.get("or")));
        }
        return new Condicion(
                nodo.path("idNodo").asText(),
                nodo.path("respuesta").asText());
    }

    /** Mapea un arreglo JSON de sub-expresiones a la lista de operandos. */
    private static List<ExpresionCondicion> buildOperandos(JsonNode arreglo) {
        List<ExpresionCondicion> operandos = new ArrayList<>();
        for (JsonNode hijo : arreglo) {
            operandos.add(buildExpresion(hijo));
        }
        return operandos;
    }

    /** Resuelve el {@code nodoDestino} de una regla contra los nodos del grafo. */
    private static Nodo buildDestino(String idNodo, GrafoClinico grafo) {
        return grafo.obtenerNodoPorId(idNodo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "nodoDestino '" + idNodo + "' no existe en el grafo"));
    }
}
