package org.signbb.backend.navegacion.datos.serializacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Une el grafo raíz con los subgrafos de los médicos en un único documento
 * plano, que es el que lee el motor de navegación.
 *
 * <p>
 * El grafo raíz es del sistema: contiene la pregunta inicial, una pregunta
 * puente por categoría y un nodo {@code referencia} por cada subgrafo
 * publicado. Cada subgrafo es un documento independiente con su propia
 * {@code preguntaInicial}. La composición:
 * <ol>
 * <li>Copia los nodos y aristas de la raíz, salvo los nodos
 * {@code referencia}.</li>
 * <li>Cada arista que llega a una {@code referencia} se redirige a la
 * {@code preguntaInicial} del subgrafo indicado por {@code grafoId}, cuyos
 * nodos y aristas se incrustan una sola vez con los ids prefijados
 * ({@code grafoId/idLocal}) para que dos médicos puedan usar el mismo id sin
 * choques. Si el subgrafo no existe, la arista se omite con un aviso.</li>
 * <li>Poda las preguntas de la raíz que quedaron sin opciones (un puente sin
 * subgrafos), lo que a su vez quita esa categoría de la pregunta inicial. La
 * pregunta inicial se conserva aunque quede sin opciones: así el contenido
 * sigue siendo reconstruible y la consulta puede reportarse como no
 * disponible.</li>
 * <li>Une las bases de reglas: las de un subgrafo se prefijan con su
 * {@code grafoId} (id de regla, {@code idNodo} de cada condición y
 * {@code nodoDestino}); las del grafo raíz ya usan ids prefijados y se copian
 * tal cual, lo que permite reglas que combinan signos de varias
 * categorías.</li>
 * </ol>
 *
 * <p>
 * El resultado no contiene referencias, así que {@link GrafoClinicoMapper}
 * construye el TAD y valida sus invariantes como con cualquier otro grafo; el
 * motor no necesita conocer la composición.
 */
public final class CompositorGrafoClinico {

    private static final Logger log = LoggerFactory.getLogger(CompositorGrafoClinico.class);

    /** Id del grafo raíz del sistema. */
    public static final String ID_RAIZ = "grafo_raiz";

    /** Separador entre el {@code grafoId} y el id local de un nodo o regla. */
    public static final String SEPARADOR = "/";

    /** Grafo y base de reglas ya aplanados. */
    public record Contenido(GrafoClinicoJson grafo, BaseReglasJson reglas) {
    }

    private CompositorGrafoClinico() {
    }

    /**
     * Compone la raíz con los subgrafos y sus bases de reglas.
     *
     * @throws IllegalArgumentException si un documento está mal formado
     *                                  (sin id, ids duplicados, subgrafo con
     *                                  referencias anidadas, base sin
     *                                  {@code grafoId}) o si dos reglas
     *                                  quedan con el mismo id.
     */
    public static Contenido componer(GrafoClinicoJson raiz, List<GrafoClinicoJson> subgrafos,
            List<BaseReglasJson> bases) {
        GrafoClinicoJson.Grafo grafoRaiz = grafoDe(raiz, "raíz");
        String preguntaInicial = GrafoClinicoMapper.inicialDe(grafoRaiz);
        if (preguntaInicial == null) {
            throw new IllegalArgumentException("el grafo raíz no declara 'preguntaInicial'");
        }
        Map<String, GrafoClinicoJson.Grafo> subgrafosPorId = indexar(subgrafos);

        Map<String, GrafoClinicoJson.NodoJson> nodos = new LinkedHashMap<>();
        Map<String, String> referencias = new LinkedHashMap<>();
        for (Map.Entry<String, GrafoClinicoJson.NodoJson> e : grafoRaiz.nodes().entrySet()) {
            if (esReferencia(e.getValue())) {
                referencias.put(e.getKey(), e.getValue().metadata().grafoId());
            } else {
                nodos.put(e.getKey(), e.getValue());
            }
        }
        Set<String> nodosRaiz = new LinkedHashSet<>(nodos.keySet());

        List<GrafoClinicoJson.Arista> aristas = new ArrayList<>();
        Set<String> incrustados = new LinkedHashSet<>();
        for (GrafoClinicoJson.Arista arista : aristasDe(grafoRaiz)) {
            if (referencias.containsKey(arista.source())) {
                throw new IllegalArgumentException(
                        "la referencia '" + arista.source() + "' no puede tener aristas de salida");
            }
            String grafoId = referencias.get(arista.target());
            if (grafoId == null) {
                aristas.add(arista);
                continue;
            }
            GrafoClinicoJson.Grafo subgrafo = subgrafosPorId.get(grafoId);
            if (subgrafo == null) {
                log.warn("La opción '{}' de '{}' apunta al grafo '{}', que no está publicado: se omite.",
                        arista.relation(), arista.source(), grafoId);
                continue;
            }
            if (incrustados.add(grafoId)) {
                incrustar(grafoId, subgrafo, nodos, aristas);
            }
            aristas.add(new GrafoClinicoJson.Arista(arista.source(), arista.relation(),
                    prefijar(grafoId, GrafoClinicoMapper.inicialDe(subgrafo)), arista.directed()));
        }

        podarPreguntasSinOpciones(preguntaInicial, nodosRaiz, nodos, aristas);
        if (!nodos.containsKey(preguntaInicial)) {
            throw new IllegalArgumentException(
                    "la pregunta inicial '" + preguntaInicial + "' no existe en el grafo raíz");
        }

        Map<String, Set<String>> opciones = new LinkedHashMap<>();
        for (GrafoClinicoJson.Arista arista : aristas) {
            opciones.computeIfAbsent(arista.source(), id -> new LinkedHashSet<>()).add(arista.relation());
        }
        GrafoClinicoJson grafo = new GrafoClinicoJson(new GrafoClinicoJson.Grafo(
                grafoRaiz.id(), grafoRaiz.label(), true, List.of(), preguntaInicial, null,
                grafoRaiz.metadata(), nodos, aristas));
        BaseReglasJson reglas = new BaseReglasJson(grafoRaiz.id(),
                unirReglas(grafoRaiz.id(), incrustados, bases, nodos.keySet(), opciones));
        return new Contenido(grafo, reglas);
    }

    /** Antepone {@code grafoId/} a un id local. */
    public static String prefijar(String grafoId, String idLocal) {
        return grafoId + SEPARADOR + idLocal;
    }

    /** Valida los subgrafos y los indexa por su {@code id}. */
    private static Map<String, GrafoClinicoJson.Grafo> indexar(List<GrafoClinicoJson> subgrafos) {
        Map<String, GrafoClinicoJson.Grafo> porId = new LinkedHashMap<>();
        for (GrafoClinicoJson documento : subgrafos == null ? List.<GrafoClinicoJson>of() : subgrafos) {
            GrafoClinicoJson.Grafo subgrafo = grafoDe(documento, "subgrafo");
            if (subgrafo.id() == null || subgrafo.id().isBlank()) {
                throw new IllegalArgumentException("un subgrafo no declara 'id'");
            }
            if (subgrafo.id().contains(SEPARADOR)) {
                throw new IllegalArgumentException(
                        "el id del subgrafo '" + subgrafo.id() + "' no puede contener '" + SEPARADOR + "'");
            }
            if (GrafoClinicoMapper.inicialDe(subgrafo) == null) {
                throw new IllegalArgumentException(
                        "el subgrafo '" + subgrafo.id() + "' no declara 'preguntaInicial'");
            }
            for (Map.Entry<String, GrafoClinicoJson.NodoJson> e : subgrafo.nodes().entrySet()) {
                if (esReferencia(e.getValue())) {
                    throw new IllegalArgumentException(
                            "el subgrafo '" + subgrafo.id() + "' tiene la referencia '" + e.getKey()
                                    + "': los subgrafos anidados no están soportados");
                }
            }
            if (porId.put(subgrafo.id(), subgrafo) != null) {
                throw new IllegalArgumentException("hay dos subgrafos con id '" + subgrafo.id() + "'");
            }
        }
        return porId;
    }

    /** Copia los nodos y aristas del subgrafo con los ids prefijados. */
    private static void incrustar(String grafoId, GrafoClinicoJson.Grafo subgrafo,
            Map<String, GrafoClinicoJson.NodoJson> nodos, List<GrafoClinicoJson.Arista> aristas) {
        for (Map.Entry<String, GrafoClinicoJson.NodoJson> e : subgrafo.nodes().entrySet()) {
            nodos.put(prefijar(grafoId, e.getKey()), e.getValue());
        }
        for (GrafoClinicoJson.Arista a : aristasDe(subgrafo)) {
            aristas.add(new GrafoClinicoJson.Arista(prefijar(grafoId, a.source()), a.relation(),
                    prefijar(grafoId, a.target()), a.directed()));
        }
    }

    /**
     * Elimina, hasta que no quede ninguna, las preguntas de la raíz sin
     * aristas de salida junto con las aristas que llegan a ellas. Así un
     * puente cuyos subgrafos no existen desaparece, y con él la opción de su
     * categoría en la pregunta inicial. La pregunta inicial no se poda: si no
     * queda ninguna categoría, se conserva sin opciones para representar que
     * la consulta no está disponible.
     */
    private static void podarPreguntasSinOpciones(String preguntaInicial, Set<String> nodosRaiz,
            Map<String, GrafoClinicoJson.NodoJson> nodos, List<GrafoClinicoJson.Arista> aristas) {
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (String id : List.copyOf(nodosRaiz)) {
                GrafoClinicoJson.NodoJson nodo = nodos.get(id);
                boolean esPregunta = nodo != null && nodo.metadata() != null
                        && "pregunta".equals(nodo.metadata().tipo());
                boolean tieneSalidas = aristas.stream().anyMatch(a -> a.source().equals(id));
                if (esPregunta && !tieneSalidas && !id.equals(preguntaInicial)) {
                    log.warn("La pregunta '{}' de la raíz quedó sin opciones: se poda.", id);
                    nodos.remove(id);
                    nodosRaiz.remove(id);
                    aristas.removeIf(a -> a.target().equals(id));
                    cambio = true;
                }
            }
        }
    }

    /**
     * Une las bases de reglas. Las del grafo raíz se copian tal cual, salvo las
     * que usan nodos de subgrafos que no están publicados; las de un subgrafo
     * incrustado se prefijan; las de un subgrafo no incrustado se omiten con un
     * aviso, porque sus nodos no están en el grafo compuesto.
     */
    private static Map<String, BaseReglasJson.ReglaJson> unirReglas(String idRaiz,
            Set<String> incrustados, List<BaseReglasJson> bases, Set<String> nodosCompuestos,
            Map<String, Set<String>> opcionesCompuestas) {
        Map<String, BaseReglasJson.ReglaJson> unidas = new LinkedHashMap<>();
        for (BaseReglasJson base : bases == null ? List.<BaseReglasJson>of() : bases) {
            if (base == null || base.rules() == null) {
                throw new IllegalArgumentException("una base de reglas no tiene la clave 'rules'");
            }
            String grafoId = base.grafoId();
            if (grafoId == null || grafoId.isBlank()) {
                throw new IllegalArgumentException("una base de reglas no declara 'grafoId'");
            }
            boolean deLaRaiz = grafoId.equals(idRaiz);
            if (!deLaRaiz && !incrustados.contains(grafoId)) {
                log.warn("Las reglas del grafo '{}' se omiten: el grafo no está en la composición.",
                        grafoId);
                continue;
            }
            for (Map.Entry<String, BaseReglasJson.ReglaJson> e : base.rules().entrySet()) {
                if (deLaRaiz && !referenciasValidas(e.getValue(), nodosCompuestos, opcionesCompuestas)) {
                    log.warn("La regla '{}' de la base del grafo raíz se omite: usa nodos u opciones "
                            + "que no están en el contenido publicado.", e.getKey());
                    continue;
                }
                String idRegla = deLaRaiz ? e.getKey() : prefijar(grafoId, e.getKey());
                BaseReglasJson.ReglaJson regla = deLaRaiz ? e.getValue() : prefijarRegla(grafoId, e.getValue());
                if (unidas.put(idRegla, regla) != null) {
                    throw new IllegalArgumentException("hay dos reglas con id '" + idRegla + "'");
                }
            }
        }
        return unidas;
    }

    /** True si el destino y todas las condiciones de la regla existen en el contenido compuesto. */
    private static boolean referenciasValidas(BaseReglasJson.ReglaJson regla, Set<String> nodos,
            Map<String, Set<String>> opciones) {
        if (regla == null) {
            return true;
        }
        if (regla.nodoDestino() != null && !nodos.contains(regla.nodoDestino())) {
            return false;
        }
        return condicionesEnElGrafo(regla.condiciones(), nodos, opciones);
    }

    /** Recorre la FND de condiciones y comprueba cada {@code idNodo} y su respuesta. */
    private static boolean condicionesEnElGrafo(JsonNode nodo, Set<String> nodos,
            Map<String, Set<String>> opciones) {
        if (nodo == null) {
            return true;
        }
        if (nodo.isArray()) {
            for (JsonNode hijo : nodo) {
                if (!condicionesEnElGrafo(hijo, nodos, opciones)) {
                    return false;
                }
            }
        } else if (nodo.isObject() && nodo.has("idNodo")) {
            String idNodo = nodo.get("idNodo").asText();
            String respuesta = nodo.path("respuesta").asText();
            return nodos.contains(idNodo)
                    && opciones.getOrDefault(idNodo, Set.of()).contains(respuesta);
        }
        return true;
    }

    /** Prefija {@code nodoDestino} y el {@code idNodo} de cada condición. */
    private static BaseReglasJson.ReglaJson prefijarRegla(String grafoId, BaseReglasJson.ReglaJson regla) {
        JsonNode condiciones = regla.condiciones() == null ? null : regla.condiciones().deepCopy();
        prefijarCondiciones(grafoId, condiciones);
        String destino = regla.nodoDestino() == null ? null : prefijar(grafoId, regla.nodoDestino());
        return new BaseReglasJson.ReglaJson(regla.nombre(), condiciones, destino, regla.nivel(),
                regla.metadata());
    }

    /**
     * Recorre la FND de condiciones (arreglo de casos o arreglo plano) y
     * prefija cada {@code idNodo}. Opera sobre una copia del árbol.
     */
    private static void prefijarCondiciones(String grafoId, JsonNode nodo) {
        if (nodo == null) {
            return;
        }
        if (nodo.isArray()) {
            for (JsonNode hijo : nodo) {
                prefijarCondiciones(grafoId, hijo);
            }
        } else if (nodo.isObject() && nodo.has("idNodo")) {
            ((ObjectNode) nodo).put("idNodo", prefijar(grafoId, nodo.get("idNodo").asText()));
        }
    }

    /** True si el nodo JSON es una {@code referencia} a otro grafo. */
    private static boolean esReferencia(GrafoClinicoJson.NodoJson nodo) {
        return nodo != null && nodo.metadata() != null
                && GrafoClinicoMapper.TIPO_REFERENCIA.equals(nodo.metadata().tipo());
    }

    /** Sección {@code graph} validada (con nodos) de un documento. */
    private static GrafoClinicoJson.Grafo grafoDe(GrafoClinicoJson documento, String rol) {
        if (documento == null || documento.graph() == null) {
            throw new IllegalArgumentException("el " + rol + " no tiene la clave 'graph'");
        }
        if (documento.graph().nodes() == null) {
            throw new IllegalArgumentException("el " + rol + " no tiene la clave 'nodes'");
        }
        return documento.graph();
    }

    /** Aristas de un grafo, vacía si el documento no las declara. */
    private static List<GrafoClinicoJson.Arista> aristasDe(GrafoClinicoJson.Grafo grafo) {
        return grafo.edges() == null ? List.of() : grafo.edges();
    }
}
