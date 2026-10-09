package org.signbb.backend.autoria.servicios;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;

/** Recorridos del lienzo siguiendo el destino de cada respuesta. */
final class Alcance {

    private Alcance() {
    }

    /** Ids de los nodos a los que se llega desde la primera pregunta (vacío si no hay). */
    static Set<String> desdeInicio(DocumentoLienzo documento) {
        return desde(documento, documento.inicio());
    }

    /** Ids de los nodos a los que se llega desde {@code origen}, incluido él mismo. */
    static Set<String> desde(DocumentoLienzo documento, String origen) {
        Set<String> vistos = new LinkedHashSet<>();
        if (origen == null || !documento.nodos().containsKey(origen)) {
            return vistos;
        }
        Deque<String> pendientes = new ArrayDeque<>();
        pendientes.push(origen);
        while (!pendientes.isEmpty()) {
            String id = pendientes.pop();
            NodoLienzo nodo = documento.nodos().get(id);
            if (nodo == null || !vistos.add(id)) {
                continue;
            }
            for (RespuestaLienzo r : nodo.respuestas()) {
                if (r.destino() != null) {
                    pendientes.push(r.destino());
                }
            }
        }
        return vistos;
    }
}
