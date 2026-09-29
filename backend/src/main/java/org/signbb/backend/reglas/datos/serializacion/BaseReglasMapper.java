package org.signbb.backend.reglas.datos.serializacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversión bidireccional entre el TAD {@link BaseReglas} y su documento JSON
 * {@link BaseReglasJson}.
 *
 * <p>
 * El {@code nodoDestino} de cada regla se persiste como id (referencia) y se
 * resuelve contra el {@link GrafoClinico} correspondiente al reconstruir el
 * TAD, fallando de inmediato si el nodo no existe en el grafo.
 */
public final class BaseReglasMapper {

    private static final JsonNodeFactory NODOS = JsonNodeFactory.instance;

    private BaseReglasMapper() {
    }

    /** Reconstruye el TAD desde el documento JSON, resolviendo destinos contra el grafo. */
    public static BaseReglas desde(BaseReglasJson documento, GrafoClinico grafo) {
        if (documento == null || documento.rules() == null) {
            throw new IllegalArgumentException("el documento no tiene la clave 'rules'");
        }
        BaseReglas tad = new BaseReglas();
        for (Map.Entry<String, BaseReglasJson.ReglaJson> e : documento.rules().entrySet()) {
            BaseReglasJson.ReglaJson reglaJson = e.getValue();
            NivelAlerta nivel = aNivel(reglaJson.nivel(), e.getKey());
            Nodo destino = grafo.obtenerNodoPorId(reglaJson.nodoDestino())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "nodoDestino '" + reglaJson.nodoDestino()
                                    + "' no existe en el grafo"));
            tad.agregarRegla(new ReglaClinica(
                    e.getKey(),
                    reglaJson.nombre(),
                    buildExpresion(reglaJson.condiciones()),
                    destino,
                    nivel));
        }
        return tad;
    }

    /** Serializa el TAD al documento JSON, referenciando destinos por id. */
    public static BaseReglasJson desde(BaseReglas tad) {
        Map<String, BaseReglasJson.ReglaJson> reglas = new LinkedHashMap<>();
        for (ReglaClinica regla : tad.todasLasReglas()) {
            reglas.put(regla.getIdRegla(), new BaseReglasJson.ReglaJson(
                    regla.getNombre(),
                    expresionAJson(regla.getExpresion()),
                    regla.getNodoDestino().getId(),
                    regla.getNivel().name(),
                    Map.of()));
        }
        return new BaseReglasJson(reglas);
    }

    /**
     * Interpreta el JSON de {@code condiciones} como una {@link ExpresionCondicion}:
     * un arreglo es un {@code AND} implícito (compatibilidad con el formato plano
     * previo); un objeto con {@code "and"} o {@code "or"} produce
     * {@link ExpresionCondicion.Y} o {@link ExpresionCondicion.O} de sus operandos
     * (anidables a cualquier profundidad); un objeto con {@code idNodo}/{@code respuesta}
     * es una hoja {@link Condicion}.
     */
    private static ExpresionCondicion buildExpresion(JsonNode nodo) {
        if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
            throw new IllegalArgumentException(
                    "la regla no tiene condiciones o vienen vacías");
        }
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
        if (arreglo == null || !arreglo.isArray() || arreglo.size() == 0) {
            throw new IllegalArgumentException(
                    "el operador and/or requiere un arreglo no vacío de condiciones");
        }
        List<ExpresionCondicion> operandos = new ArrayList<>();
        for (JsonNode hijo : arreglo) {
            operandos.add(buildExpresion(hijo));
        }
        return operandos;
    }

    /** Serializa una {@link ExpresionCondicion} al formato JSON de {@code condiciones}. */
    private static JsonNode expresionAJson(ExpresionCondicion expresion) {
        if (expresion instanceof Condicion hoja) {
            ObjectNode nodo = NODOS.objectNode();
            nodo.put("idNodo", hoja.idNodo());
            nodo.put("respuesta", hoja.respuesta());
            return nodo;
        }
        ObjectNode nodo = NODOS.objectNode();
        if (expresion instanceof ExpresionCondicion.Y y) {
            nodo.set("and", operandosAJson(y.operandos()));
        } else if (expresion instanceof ExpresionCondicion.O o) {
            nodo.set("or", operandosAJson(o.operandos()));
        } else {
            throw new IllegalStateException(
                    "Expresión de condiciones desconocida: " + expresion);
        }
        return nodo;
    }

    /** Serializa una lista de operandos a un arreglo JSON. */
    private static ArrayNode operandosAJson(List<ExpresionCondicion> operandos) {
        ArrayNode arreglo = NODOS.arrayNode();
        for (ExpresionCondicion e : operandos) {
            arreglo.add(expresionAJson(e));
        }
        return arreglo;
    }

    /** Convierte el nivel del JSON al enum, rechazando NORMAL y valores inválidos. */
    private static NivelAlerta aNivel(String nivel, String idRegla) {
        if (nivel == null) {
            throw new IllegalArgumentException(
                    "la regla '" + idRegla + "' no tiene nivel");
        }
        NivelAlerta valor = NivelAlerta.valueOf(nivel);
        if (valor == NivelAlerta.NORMAL) {
            throw new IllegalArgumentException(
                    "la regla '" + idRegla + "' no puede tener nivel NORMAL");
        }
        return valor;
    }
}
