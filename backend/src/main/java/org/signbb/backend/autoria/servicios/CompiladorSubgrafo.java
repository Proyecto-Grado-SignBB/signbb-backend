package org.signbb.backend.autoria.servicios;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;

/**
 * Traduce el lienzo de un borrador al documento de un subgrafo
 * ({@code GrafoClinicoJson}), el mismo formato de los subgrafos del sistema en
 * {@code static/grafo_sistema/}.
 *
 * <ul>
 * <li>Solo se compilan los nodos a los que se llega desde la primera
 * pregunta; los sueltos se omiten.</li>
 * <li>Una pregunta queda como {@code tipo: pregunta}, con
 * {@code SELECCION_MULTIPLE} si se pueden marcar varias respuestas y
 * {@code SELECCION_UNICA} si no.</li>
 * <li>Una conclusión queda como {@code tipo: terminal}, con su desenlace y el
 * mensaje para la cuidadora como {@code label}.</li>
 * <li>Cada respuesta es una arista {@code pregunta --texto--> destino}.</li>
 * <li>El {@code peso} de un nodo es la mayor gravedad de las respuestas que
 * llegan a él; la primera pregunta, a la que no llega ninguna, lleva
 * {@link GrafoClinicoMapper#PESO_MINIMO}.</li>
 * </ul>
 *
 * <p>
 * Supone un lienzo sin avisos bloqueantes ({@link ValidadorLienzo}); las
 * respuestas sin destino se omiten.
 */
public final class CompiladorSubgrafo {

    private CompiladorSubgrafo() {
    }

    /**
     * Compila el borrador.
     *
     * @param borrador borrador con sus datos y su lienzo
     * @param fecha    fecha de la publicación, que va en la metadata
     */
    public static GrafoClinicoJson compilar(BorradorSubgrafoEntity borrador, Instant fecha) {
        DocumentoLienzo documento = borrador.getDocumento();
        Set<String> alcanzables = Alcance.desdeInicio(documento);
        Map<String, Integer> pesos = pesos(documento, alcanzables);

        Map<String, GrafoClinicoJson.NodoJson> nodos = new LinkedHashMap<>();
        List<GrafoClinicoJson.Arista> aristas = new ArrayList<>();
        for (NodoLienzo nodo : documento.nodos().values()) {
            if (!alcanzables.contains(nodo.id())) {
                continue;
            }
            int peso = pesos.getOrDefault(nodo.id(), GrafoClinicoMapper.PESO_MINIMO);
            nodos.put(nodo.id(), new GrafoClinicoJson.NodoJson(nodo.texto(), metadata(nodo, peso)));
            for (RespuestaLienzo r : nodo.respuestas()) {
                if (r.destino() != null && alcanzables.contains(r.destino())) {
                    aristas.add(new GrafoClinicoJson.Arista(nodo.id(), r.texto(), r.destino(), true));
                }
            }
        }

        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo(
                borrador.getGrafoId(),
                borrador.getNombre(),
                true,
                List.of(borrador.getCategoria()),
                documento.inicio(),
                null,
                metadataGrafo(borrador, fecha),
                nodos,
                aristas));
    }

    /** Mayor gravedad de las respuestas que llegan a cada nodo publicado. */
    private static Map<String, Integer> pesos(DocumentoLienzo documento, Set<String> alcanzables) {
        Map<String, Integer> pesos = new HashMap<>();
        for (String id : alcanzables) {
            for (RespuestaLienzo r : documento.nodos().get(id).respuestas()) {
                if (r.destino() != null) {
                    pesos.merge(r.destino(), r.gravedad().peso(), Math::max);
                }
            }
        }
        return pesos;
    }

    private static GrafoClinicoJson.MetadataNodo metadata(NodoLienzo nodo, int peso) {
        if (nodo.tipo() == TipoNodo.CONCLUSION) {
            return new GrafoClinicoJson.MetadataNodo("terminal", null,
                    nodo.desenlace() == null ? null : nodo.desenlace().valor(), null, peso, false, List.of());
        }
        String interaccion = nodo.variasRespuestas() ? "SELECCION_MULTIPLE" : "SELECCION_UNICA";
        boolean conImagenes = !nodo.imagenes().isEmpty();
        return new GrafoClinicoJson.MetadataNodo("pregunta", interaccion, null, null, peso, conImagenes,
                nodo.imagenes());
    }

    /** Metadata del grafo; los campos vacíos no se escriben. */
    private static Map<String, Object> metadataGrafo(BorradorSubgrafoEntity borrador, Instant fecha) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        poner(metadata, "autor", borrador.getAutor());
        poner(metadata, "institucion", borrador.getInstitucion());
        Instant creado = borrador.getCreadoEn() != null ? borrador.getCreadoEn() : fecha;
        poner(metadata, "fechaCreacion", creado == null ? null : creado.toString());
        poner(metadata, "fechaPublicacion", fecha == null ? null : fecha.toString());
        poner(metadata, "descripcion", "Subgrafo creado en el módulo de autoría.");
        poner(metadata, "etiquetaOpcion", borrador.getEtiquetaOpcion());
        return metadata;
    }

    private static void poner(Map<String, Object> metadata, String clave, String valor) {
        if (valor != null && !valor.isBlank()) {
            metadata.put(clave, valor);
        }
    }
}
