package org.signbb.backend.reglas.datos.serializacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
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
 * La premisa de cada regla se expresa en forma normal disyuntiva (FND): una
 * lista de casos (disyunción) donde cada caso es una lista de condiciones
 * (conjunción). El {@code nodoDestino} de cada regla se persiste como id
 * (referencia) y se resuelve contra el {@link GrafoClinico} correspondiente al
 * reconstruir el TAD, fallando de inmediato si el nodo no existe en el grafo.
 * Cada condición se revisa también contra el grafo: su {@code idNodo} debe ser
 * una pregunta y su {@code respuesta} una de las opciones de esa pregunta; si
 * no, la regla nunca podría dispararse y se rechaza al cargarla.
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
            List<Caso> casos = buildCasos(reglaJson.condiciones());
            validarCondiciones(e.getKey(), casos, grafo);
            tad.agregarRegla(ReglaClinica.conCasos(
                    e.getKey(),
                    reglaJson.nombre(),
                    casos,
                    destino,
                    nivel));
        }
        return tad;
    }

    /**
     * Serializa el TAD al documento JSON, referenciando destinos por id. El
     * documento resultante no declara {@code grafoId}: el TAD no lo almacena.
     */
    public static BaseReglasJson desde(BaseReglas tad) {
        Map<String, BaseReglasJson.ReglaJson> reglas = new LinkedHashMap<>();
        for (ReglaClinica regla : tad.todasLasReglas()) {
            reglas.put(regla.getIdRegla(), new BaseReglasJson.ReglaJson(
                    regla.getNombre(),
                    casosAJson(regla.getCasos()),
                    regla.getNodoDestino().getId(),
                    regla.getNivel().name(),
                    Map.of()));
        }
        return new BaseReglasJson(null, reglas);
    }

    /**
     * Interpreta el JSON de {@code condiciones} como la FND de una regla: un
     * arreglo de arreglos, donde cada arreglo interno es un caso (conjunción).
     * Por compatibilidad con el formato plano previo, un arreglo de objetos
     * {@code {idNodo, respuesta}} se interpreta como un único caso. Cualquier
     * otra forma (vacía, mixta, u objeto {@code and}/{@code or}) es rechazada.
     */
    private static List<Caso> buildCasos(JsonNode nodo) {
        if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
            throw new IllegalArgumentException(
                    "la regla no tiene condiciones o vienen vacías");
        }
        if (!nodo.isArray() || nodo.size() == 0) {
            throw new IllegalArgumentException(
                    "las condiciones deben ser un arreglo no vacío de casos");
        }
        if (nodo.get(0).isArray()) {
            List<Caso> casos = new ArrayList<>();
            for (JsonNode hijo : nodo) {
                casos.add(buildCaso(hijo));
            }
            return casos;
        }
        if (nodo.get(0).isObject()) {
            return List.of(buildCaso(nodo));
        }
        throw new IllegalArgumentException(
                "cada caso debe ser un arreglo de condiciones o un objeto {idNodo, respuesta}");
    }

    /**
     * Revisa cada condición de la regla contra el grafo: el nodo existe, es
     * una pregunta y la respuesta es una de sus opciones.
     */
    private static void validarCondiciones(String idRegla, List<Caso> casos, GrafoClinico grafo) {
        for (Caso caso : casos) {
            for (Condicion c : caso.condiciones()) {
                Nodo nodo = grafo.obtenerNodoPorId(c.idNodo())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "la regla '" + idRegla + "' tiene una condición sobre '" + c.idNodo()
                                        + "', que no existe en el grafo"));
                if (nodo.esTerminal()) {
                    throw new IllegalArgumentException(
                            "la regla '" + idRegla + "' tiene una condición sobre el terminal '"
                                    + c.idNodo() + "': solo las preguntas tienen respuestas");
                }
                boolean esOpcion = grafo.obtenerOpciones(nodo).stream()
                        .anyMatch(r -> r.getCondicion().equals(c.respuesta()));
                if (!esOpcion) {
                    throw new IllegalArgumentException(
                            "la regla '" + idRegla + "' espera la respuesta '" + c.respuesta()
                                    + "' en '" + c.idNodo() + "', que no es una de sus opciones");
                }
            }
        }
    }

    /** Mapea un arreglo JSON de condiciones a la conjunción {@link Caso}. */
    private static Caso buildCaso(JsonNode arreglo) {
        if (arreglo == null || !arreglo.isArray() || arreglo.size() == 0) {
            throw new IllegalArgumentException(
                    "un caso requiere un arreglo no vacío de condiciones");
        }
        List<Condicion> condiciones = new ArrayList<>();
        for (JsonNode hijo : arreglo) {
            if (!hijo.isObject()) {
                throw new IllegalArgumentException(
                        "cada condición debe ser un objeto {idNodo, respuesta}");
            }
            condiciones.add(new Condicion(
                    hijo.path("idNodo").asText(),
                    hijo.path("respuesta").asText()));
        }
        return new Caso(condiciones);
    }

    /** Serializa la FND de una regla al formato JSON de {@code condiciones}. */
    private static ArrayNode casosAJson(List<Caso> casos) {
        ArrayNode arreglo = NODOS.arrayNode();
        for (Caso caso : casos) {
            ArrayNode casoJson = NODOS.arrayNode();
            for (Condicion c : caso.condiciones()) {
                ObjectNode condicion = NODOS.objectNode();
                condicion.put("idNodo", c.idNodo());
                condicion.put("respuesta", c.respuesta());
                casoJson.add(condicion);
            }
            arreglo.add(casoJson);
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
