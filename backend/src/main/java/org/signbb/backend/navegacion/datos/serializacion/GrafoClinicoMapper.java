package org.signbb.backend.navegacion.datos.serializacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * aristas a nodos inexistentes o condiciones repetidas fallan aquí). La
 * dirección TAD → JSON pierde los metadatos de presentación (label del grafo,
 * categorías) porque el TAD no los almacena; la dirección JSON → TAD
 * reconstruye el grafo con los umbrales por defecto.
 */
public final class GrafoClinicoMapper {

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
                    ? new GrafoClinicoJson.MetadataNodo(null, null, null, null, null)
                    : nodoJson.metadata();
            Nodo nodo = new Nodo(
                    e.getKey(),
                    normalizarTipo(meta.tipo()),
                    nodoJson.label(),
                    meta.peso() == null ? 0 : meta.peso(),
                    Boolean.TRUE.equals(meta.archivos()),
                    meta.src());
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

        if (grafo.nodoInicial() != null) {
            Nodo inicial = nodosPorId.get(grafo.nodoInicial());
            if (inicial == null) {
                throw new IllegalArgumentException(
                        "nodoInicial '" + grafo.nodoInicial() + "' no existe entre los nodos");
            }
            tad.setNodoInicial(inicial);
        }
        return tad;
    }

    /** Serializa el TAD al documento JSON, referenciando nodos por id. */
    public static GrafoClinicoJson desde(GrafoClinico tad) {
        Map<String, GrafoClinicoJson.NodoJson> nodos = new LinkedHashMap<>();
        List<GrafoClinicoJson.Arista> aristas = new ArrayList<>();
        for (Nodo nodo : tad.obtenerNodos()) {
            nodos.put(nodo.getId(), new GrafoClinicoJson.NodoJson(
                    nodo.getContenido(),
                    new GrafoClinicoJson.MetadataNodo(
                            nodo.getTipo(),
                            nodo.getPeso(),
                            nodo.tieneArchivos(),
                            nodo.getSrc(),
                            List.of())));
            for (Respuesta r : tad.obtenerOpciones(nodo)) {
                aristas.add(new GrafoClinicoJson.Arista(
                        r.getOrigen().getId(),
                        r.getCondicion(),
                        r.getDestino().getId(),
                        Map.of()));
            }
        }
        String nodoInicial = tad.obtenerNodoInicial().map(Nodo::getId).orElse(null);
        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo(
                null, Map.of(), nodoInicial, nodos, aristas));
    }

    /** El JSON usa "inicio" y "consulta médica"; el TAD espera otros valores. */
    private static String normalizarTipo(String tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case "inicio" -> "pregunta";
            case "consulta médica" -> "consulta_medica";
            default -> tipo;
        };
    }
}
