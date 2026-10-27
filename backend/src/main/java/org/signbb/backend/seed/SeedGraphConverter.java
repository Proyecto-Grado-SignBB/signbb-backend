package org.signbb.backend.seed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

import tools.jackson.databind.JsonNode;

/**
 * Convierte los documentos JSON del seed al modelo que edita el médico
 * ({@code DocumentoLienzo} y {@code DocumentoBaseRegla}), para que el seed use
 * el mismo flujo de publicación que la app de autoría.
 *
 * <p>
 * La gravedad de cada respuesta se toma del peso del nodo destino: 3 =
 * urgencia, 2 = consultar médico, 1 = normal. Así el grafo compilado conserva
 * los pesos que ya tienen los JSON del seed.
 */
public final class SeedGraphConverter {

    private SeedGraphConverter() {
    }

    /** Convierte un subgrafo JSON al lienzo del editor. */
    public static DocumentoLienzo aLienzo(GrafoClinicoJson documento) {
        if (documento == null || documento.graph() == null) {
            throw new IllegalArgumentException("el subgrafo no tiene la clave 'graph'");
        }
        GrafoClinicoJson.Grafo grafo = documento.graph();
        if (grafo.nodes() == null) {
            throw new IllegalArgumentException("el subgrafo no tiene la clave 'nodes'");
        }
        String inicio = grafo.preguntaInicial() != null ? grafo.preguntaInicial() : grafo.nodoInicial();
        if (inicio == null || inicio.isBlank()) {
            throw new IllegalArgumentException("el subgrafo no declara 'preguntaInicial'");
        }

        Map<String, List<GrafoClinicoJson.Arista>> salientes = new LinkedHashMap<>();
        for (GrafoClinicoJson.Arista arista : aristas(grafo)) {
            salientes.computeIfAbsent(arista.source(), id -> new ArrayList<>()).add(arista);
        }

        Map<String, NodoLienzo> nodos = new LinkedHashMap<>();
        int secuencia = 0;
        for (Map.Entry<String, GrafoClinicoJson.NodoJson> entry : grafo.nodes().entrySet()) {
            String id = entry.getKey();
            GrafoClinicoJson.NodoJson nodo = entry.getValue();
            GrafoClinicoJson.MetadataNodo meta = metadata(nodo);
            if (GrafoClinicoJsonAccess.tipoReferencia(meta)) {
                throw new IllegalArgumentException("el subgrafo '" + grafo.id()
                        + "' contiene una referencia: los subgrafos anidados no están soportados");
            }
            if (GrafoClinicoJsonAccess.tipoTerminal(meta)) {
                nodos.put(id, new NodoLienzo(id, TipoNodo.CONCLUSION, nodo.label(), false,
                        desenlace(id, meta.desenlace()), List.of(), imagenes(meta)));
                continue;
            }

            List<RespuestaLienzo> respuestas = new ArrayList<>();
            for (GrafoClinicoJson.Arista arista : salientes.getOrDefault(id, List.of())) {
                GrafoClinicoJson.NodoJson destino = grafo.nodes().get(arista.target());
                respuestas.add(new RespuestaLienzo("r" + (++secuencia), arista.relation(),
                        gravedad(destino), arista.target()));
            }
            boolean multiple = "SELECCION_MULTIPLE".equals(meta.tipoInteraccion());
            nodos.put(id, new NodoLienzo(id, TipoNodo.PREGUNTA, nodo.label(), multiple, null,
                    respuestas, imagenes(meta)));
        }
        return new DocumentoLienzo(inicio, secuencia + nodos.size() + 10, nodos);
    }

    /** Convierte una base de reglas JSON al documento del editor de reglas. */
    public static DocumentoBaseRegla aDocumentoReglas(BaseReglasJson base) {
        if (base == null || base.rules() == null) {
            throw new IllegalArgumentException("la base de reglas no tiene la clave 'rules'");
        }
        Map<String, BorradorRegla> reglas = new LinkedHashMap<>();
        for (Map.Entry<String, BaseReglasJson.ReglaJson> entry : base.rules().entrySet()) {
            BaseReglasJson.ReglaJson regla = entry.getValue();
            if (regla == null) {
                continue;
            }
            Object descripcionValor = regla.metadata() == null ? null
                    : regla.metadata().get("descripcion");
            String descripcion = descripcionValor instanceof String texto && !texto.isBlank()
                    ? texto
                    : null;
            reglas.put(entry.getKey(), new BorradorRegla(
                    regla.nombre(),
                    descripcion,
                    casos(entry.getKey(), regla.condiciones()),
                    nivel(entry.getKey(), regla.nivel()),
                    regla.nodoDestino()));
        }
        return new DocumentoBaseRegla(reglas.size() + 10, reglas);
    }

    private static List<GrafoClinicoJson.Arista> aristas(GrafoClinicoJson.Grafo grafo) {
        return grafo.edges() == null ? List.of() : grafo.edges();
    }

    private static GrafoClinicoJson.MetadataNodo metadata(GrafoClinicoJson.NodoJson nodo) {
        return nodo.metadata() == null
                ? new GrafoClinicoJson.MetadataNodo(null, null, null, null, 1, false, List.of())
                : nodo.metadata();
    }

    private static List<String> imagenes(GrafoClinicoJson.MetadataNodo meta) {
        return meta.src() == null ? List.of() : List.copyOf(meta.src());
    }

    private static Gravedad gravedad(GrafoClinicoJson.NodoJson destino) {
        Integer peso = destino != null && destino.metadata() != null ? destino.metadata().peso() : null;
        int valor = peso == null ? 1 : Math.max(1, Math.min(3, peso));
        return switch (valor) {
            case 3 -> Gravedad.GRAVE;
            case 2 -> Gravedad.MODERADA;
            default -> Gravedad.LEVE;
        };
    }

    private static Desenlace desenlace(String idNodo, String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("el terminal '" + idNodo + "' no declara 'desenlace'");
        }
        return switch (valor) {
            case "urgencia" -> Desenlace.URGENCIA;
            case "consulta_medica" -> Desenlace.CONSULTA_MEDICA;
            case "recomendacion" -> Desenlace.RECOMENDACION;
            default -> throw new IllegalArgumentException(
                    "el terminal '" + idNodo + "' tiene desenlace inválido: " + valor);
        };
    }

    private static NivelAlerta nivel(String idRegla, String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("la regla '" + idRegla + "' no declara 'nivel'");
        }
        return NivelAlerta.valueOf(valor);
    }

    private static List<Caso> casos(String idRegla, JsonNode condiciones) {
        if (condiciones == null || !condiciones.isArray() || condiciones.size() == 0) {
            throw new IllegalArgumentException("la regla '" + idRegla + "' no tiene condiciones");
        }
        List<Caso> casos = new ArrayList<>();
        for (JsonNode casoJson : condiciones) {
            List<Condicion> condicionesCaso = new ArrayList<>();
            for (JsonNode condicionJson : casoJson) {
                condicionesCaso.add(new Condicion(
                        condicionJson.path("idNodo").asText(),
                        condicionJson.path("respuesta").asText()));
            }
            casos.add(new Caso(condicionesCaso));
        }
        return casos;
    }

    /** Accesos a constantes del mapper sin exponerlas en el resto del seed. */
    private static final class GrafoClinicoJsonAccess {
        private GrafoClinicoJsonAccess() {
        }

        static boolean tipoReferencia(GrafoClinicoJson.MetadataNodo meta) {
            return "referencia".equals(meta.tipo());
        }

        static boolean tipoTerminal(GrafoClinicoJson.MetadataNodo meta) {
            return "terminal".equals(meta.tipo());
        }
    }
}
