package org.signbb.backend.navegacion.datos.serializacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.Respuesta;

/**
 * Conversión bidireccional entre el TAD {@link GrafoClinico} y su documento
 * JSON {@link GrafoClinicoJson}.
 *
 * <p>
 * Ambas direcciones usan solo las operaciones públicas del TAD, de modo que
 * los invariantes del grafo se validan al reconstruirlo (ids duplicados,
 * aristas a nodos inexistentes o condiciones repetidas fallan aquí). Además se
 * validan las reglas propias del formato: el {@code peso} de cada nodo debe
 * estar en la escala 1–3 y los nodos {@code referencia} no llegan al TAD (se
 * resuelven antes con {@link CompositorGrafoClinico}). La dirección TAD → JSON
 * pierde los metadatos de presentación (id, label y categoría del grafo)
 * porque el TAD no los almacena; la dirección JSON → TAD reconstruye el grafo
 * con los umbrales por defecto.
 */
public final class GrafoClinicoMapper {

    /** Tipo JSON de un nodo terminal; su desenlace va en {@code desenlace}. */
    static final String TIPO_TERMINAL = "terminal";

    /** Tipo JSON de un nodo que apunta a otro grafo por {@code grafoId}. */
    static final String TIPO_REFERENCIA = "referencia";

    /** Desenlaces admitidos para un nodo {@code terminal}. */
    private static final Set<String> DESENLACES = Set.of("recomendacion", "consulta_medica", "urgencia");

    /** Peso mínimo de la escala (urgencia baja). */
    public static final int PESO_MINIMO = 1;

    /** Peso máximo de la escala (urgente). */
    public static final int PESO_MAXIMO = 3;

    private GrafoClinicoMapper() {
    }

    /** Reconstruye el TAD desde el documento JSON. */
    public static GrafoClinico desde(GrafoClinicoJson documento) {
        if (documento == null || documento.graph() == null) {
            throw new IllegalArgumentException("el documento no tiene la clave 'graph'");
        }
        GrafoClinicoJson.Grafo grafo = documento.graph();
        if (grafo.nodes() == null) {
            throw new IllegalArgumentException("falta la clave 'nodes' en 'graph'");
        }

        GrafoClinico tad = new GrafoClinico();
        Map<String, Nodo> nodosPorId = new LinkedHashMap<>();
        for (Map.Entry<String, GrafoClinicoJson.NodoJson> e : grafo.nodes().entrySet()) {
            GrafoClinicoJson.NodoJson nodoJson = e.getValue();
            GrafoClinicoJson.MetadataNodo meta = nodoJson.metadata() == null
                    ? new GrafoClinicoJson.MetadataNodo(null, null, null, null, null, null, null)
                    : nodoJson.metadata();
            Nodo nodo = new Nodo(
                    e.getKey(),
                    tipoDelTad(e.getKey(), meta),
                    nodoJson.label(),
                    validarPeso(e.getKey(), meta.peso()),
                    Boolean.TRUE.equals(meta.archivos()),
                    meta.src(),
                    meta.tipoInteraccion());
            nodosPorId.put(e.getKey(), nodo);
            tad.agregarNodo(nodo);
        }

        List<GrafoClinicoJson.Arista> aristas = grafo.edges() == null
                ? List.of()
                : grafo.edges();
        for (GrafoClinicoJson.Arista arista : aristas) {
            Nodo origen = nodosPorId.get(arista.source());
            Nodo destino = nodosPorId.get(arista.target());
            if (origen == null || destino == null) {
                throw new IllegalArgumentException(
                        "la arista '" + arista.source() + "' --[" + arista.relation()
                                + "]--> '" + arista.target() + "' referencia nodos inexistentes");
            }
            tad.conectarRespuestas(origen, destino, arista.relation());
        }

        String inicial = inicialDe(grafo);
        if (inicial != null) {
            Nodo nodoInicial = nodosPorId.get(inicial);
            if (nodoInicial == null) {
                throw new IllegalArgumentException(
                        "preguntaInicial '" + inicial + "' no existe entre los nodos");
            }
            tad.setNodoInicial(nodoInicial);
        }
        return tad;
    }

    /** Serializa el TAD al documento JSON, referenciando nodos por id. */
    public static GrafoClinicoJson desde(GrafoClinico tad) {
        Map<String, GrafoClinicoJson.NodoJson> nodos = new LinkedHashMap<>();
        List<GrafoClinicoJson.Arista> aristas = new ArrayList<>();
        for (Nodo nodo : tad.obtenerNodos()) {
            boolean terminal = nodo.esTerminal();
            nodos.put(nodo.getId(), new GrafoClinicoJson.NodoJson(
                    nodo.getContenido(),
                    new GrafoClinicoJson.MetadataNodo(
                            terminal ? TIPO_TERMINAL : nodo.getTipo(),
                            nodo.getTipoInteraccion(),
                            terminal ? nodo.getTipo() : null,
                            null,
                            nodo.getPeso(),
                            nodo.tieneArchivos(),
                            nodo.getSrc())));
            for (Respuesta r : tad.obtenerOpciones(nodo)) {
                aristas.add(new GrafoClinicoJson.Arista(
                        r.getOrigen().getId(),
                        r.getCondicion(),
                        r.getDestino().getId(),
                        true));
            }
        }
        String preguntaInicial = tad.obtenerNodoInicial().map(Nodo::getId).orElse(null);
        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo(
                null, null, true, List.of(), preguntaInicial, null, Map.of(), nodos, aristas));
    }

    /**
     * Nodo inicial declarado por el documento: {@code preguntaInicial} o, en
     * documentos del formato previo, {@code nodoInicial}.
     */
    static String inicialDe(GrafoClinicoJson.Grafo grafo) {
        return grafo.preguntaInicial() != null ? grafo.preguntaInicial() : grafo.nodoInicial();
    }

    /**
     * Traduce el tipo del JSON al tipo del TAD. Un {@code terminal} toma como
     * tipo su {@code desenlace}; una {@code referencia} se rechaza porque debe
     * resolverse antes con {@link CompositorGrafoClinico}. Los tipos del
     * formato previo ({@code inicio}, {@code consulta médica},
     * {@code recomendacion}) se siguen aceptando.
     */
    private static String tipoDelTad(String idNodo, GrafoClinicoJson.MetadataNodo meta) {
        String tipo = meta.tipo();
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case TIPO_TERMINAL -> {
                if (!DESENLACES.contains(meta.desenlace())) {
                    throw new IllegalArgumentException(
                            "el terminal '" + idNodo + "' tiene desenlace inválido: '"
                                    + meta.desenlace() + "'. Debe ser uno de " + DESENLACES);
                }
                yield meta.desenlace();
            }
            case TIPO_REFERENCIA -> throw new IllegalArgumentException(
                    "el nodo '" + idNodo + "' es una referencia a '" + meta.grafoId()
                            + "': debe resolverse con CompositorGrafoClinico antes de construir el TAD");
            case "inicio" -> "pregunta";
            case "consulta médica" -> "consulta_medica";
            default -> tipo;
        };
    }

    /** Exige que el peso exista y esté en la escala 1–3. */
    private static int validarPeso(String idNodo, Integer peso) {
        if (peso == null || peso < PESO_MINIMO || peso > PESO_MAXIMO) {
            throw new IllegalArgumentException(
                    "el nodo '" + idNodo + "' tiene peso " + peso + ": debe ir de "
                            + PESO_MINIMO + " (urgencia baja) a " + PESO_MAXIMO + " (urgente)");
        }
        return peso;
    }
}
