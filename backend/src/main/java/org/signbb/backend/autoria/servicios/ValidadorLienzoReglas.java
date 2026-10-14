package org.signbb.backend.autoria.servicios;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.Aviso.Codigo;
import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;

import tools.jackson.databind.JsonNode;

/**
 * Revisa que las reglas de un grafo sigan apuntando a nodos y respuestas que
 * existen en el lienzo del subgrafo. Cuando el médico elimina un nodo (o una
 * rama) que una regla usa como condición o como destino, la regla queda
 * colgando: al publicar el subgrafo el contenido compuesto no se podría
 * reconstruir. Este validador lo detecta y devuelve avisos bloqueantes, en
 * lenguaje humano, para que el médico corrija la regla antes de publicar.
 *
 * <p>
 * Se valida tanto el borrador de reglas ({@link DocumentoBaseRegla}) como la
 * base publicada ({@link BaseReglasJson}); en ambos casos los ids de los nodos
 * son locales al subgrafo. Los avisos usan {@code REGLA_DESTINO_INEXISTENTE} y
 * {@code REGLA_CONDICION_INVALIDA}.
 */
public final class ValidadorLienzoReglas {

    private ValidadorLienzoReglas() {
    }

    /** Avisos bloqueantes del borrador de reglas contra el lienzo. */
    public static List<Aviso> validar(DocumentoLienzo lienzo, DocumentoBaseRegla reglas) {
        if (lienzo == null || reglas == null) {
            return List.of();
        }
        List<RefRegla> refs = new ArrayList<>();
        for (Map.Entry<String, BorradorRegla> e : reglas.reglas().entrySet()) {
            BorradorRegla regla = e.getValue();
            if (regla == null) {
                continue;
            }
            List<RefCondicion> condiciones = new ArrayList<>();
            for (Caso caso : regla.casos()) {
                for (Condicion c : caso.condiciones()) {
                    condiciones.add(new RefCondicion(c.idNodo(), c.respuesta()));
                }
            }
            refs.add(new RefRegla(e.getKey(), regla.nombre(), regla.nodoDestino(), condiciones));
        }
        return validar(lienzo, refs);
    }

    /** Avisos bloqueantes de la base publicada contra el lienzo. */
    public static List<Aviso> validarPublicada(DocumentoLienzo lienzo, BaseReglasJson reglas) {
        if (lienzo == null || reglas == null || reglas.rules() == null) {
            return List.of();
        }
        List<RefRegla> refs = new ArrayList<>();
        for (Map.Entry<String, BaseReglasJson.ReglaJson> e : reglas.rules().entrySet()) {
            BaseReglasJson.ReglaJson regla = e.getValue();
            if (regla == null) {
                continue;
            }
            List<RefCondicion> condiciones = new ArrayList<>();
            recolectarCondiciones(regla.condiciones(), condiciones);
            refs.add(new RefRegla(e.getKey(), regla.nombre(), regla.nodoDestino(), condiciones));
        }
        return validar(lienzo, refs);
    }

    private static List<Aviso> validar(DocumentoLienzo lienzo, List<RefRegla> reglas) {
        List<Aviso> avisos = new ArrayList<>();
        Map<String, NodoLienzo> nodos = lienzo.nodos();
        for (RefRegla regla : reglas) {
            String nombre = regla.nombre() == null || regla.nombre().isBlank()
                    ? regla.idRegla() : regla.nombre();
            if (regla.nodoDestino() != null && !regla.nodoDestino().isBlank()
                    && !nodos.containsKey(regla.nodoDestino())) {
                avisos.add(new Aviso(Codigo.REGLA_DESTINO_INEXISTENTE, true,
                        "La regla \"" + nombre + "\" lleva a un nodo que ya no está en el lienzo: "
                                + "actualízala.",
                        regla.nodoDestino(), null));
            }
            for (RefCondicion c : regla.condiciones()) {
                NodoLienzo nodo = c.idNodo() == null ? null : nodos.get(c.idNodo());
                if (nodo == null || nodo.tipo() != TipoNodo.PREGUNTA) {
                    avisos.add(new Aviso(Codigo.REGLA_CONDICION_INVALIDA, true,
                            "La regla \"" + nombre + "\" usa \"" + c.idNodo() + "\", que ya no está en el "
                                    + "lienzo o no es una pregunta: actualízala.",
                            c.idNodo(), null));
                    continue;
                }
                boolean esOpcion = nodo.respuestas().stream().anyMatch(r -> r.texto() != null
                        && r.texto().equals(c.respuesta()));
                if (!esOpcion) {
                    avisos.add(new Aviso(Codigo.REGLA_CONDICION_INVALIDA, true,
                            "La regla \"" + nombre + "\" espera \"" + c.respuesta() + "\" en \""
                                    + (nodo.texto() == null || nodo.texto().isBlank() ? nodo.id() : nodo.texto())
                                    + "\", que ya no es una de sus opciones: actualízala.",
                            c.idNodo(), null));
                }
            }
        }
        return avisos;
    }

    /** Recorre la FND de condiciones (arreglo de casos o arreglo plano). */
    private static void recolectarCondiciones(JsonNode nodo, List<RefCondicion> out) {
        if (nodo == null) {
            return;
        }
        if (nodo.isArray()) {
            for (JsonNode hijo : nodo) {
                recolectarCondiciones(hijo, out);
            }
        } else if (nodo.isObject() && nodo.has("idNodo")) {
            out.add(new RefCondicion(nodo.path("idNodo").asText(), nodo.path("respuesta").asText()));
        }
    }

    private record RefCondicion(String idNodo, String respuesta) {
    }

    private record RefRegla(String idRegla, String nombre, String nodoDestino, List<RefCondicion> condiciones) {
    }
}
