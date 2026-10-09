package org.signbb.backend.autoria.servicios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.Aviso.Codigo;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;

/**
 * Revisa el lienzo y devuelve los avisos para el médico, en lenguaje humano.
 *
 * <p>
 * Solo se revisan los nodos a los que se llega desde la primera pregunta,
 * porque son los que se publican. Un nodo suelto da un único aviso, que no
 * bloquea: al publicar se omite. Que dos respuestas de distinta gravedad
 * lleguen a la misma pregunta no se avisa; al compilar se toma la mayor.
 */
public final class ValidadorLienzo {

    /** Respuestas mínimas de una pregunta. */
    static final int MINIMO_RESPUESTAS = 2;

    private ValidadorLienzo() {
    }

    /** Avisos del lienzo, en el orden de los nodos. */
    public static List<Aviso> validar(DocumentoLienzo documento) {
        List<Aviso> avisos = new ArrayList<>();
        if (documento.nodo(documento.inicio()).isEmpty()) {
            avisos.add(new Aviso(Codigo.SIN_INICIO, true,
                    "Todavía no hay una primera pregunta: empieza con \"+ Primera pregunta\".", null, null));
        }
        Set<String> alcanzables = Alcance.desdeInicio(documento);

        for (NodoLienzo nodo : documento.nodos().values()) {
            if (!alcanzables.contains(nodo.id())) {
                avisos.add(new Aviso(Codigo.NODO_SUELTO, false, "\"" + nombre(nodo)
                        + "\" no está conectada con ninguna respuesta: no se publicará.", nodo.id(), null));
            } else if (nodo.tipo() == TipoNodo.CONCLUSION) {
                revisarConclusion(nodo, avisos);
            } else {
                revisarPregunta(documento, nodo, avisos);
            }
        }
        buscarCiclos(documento, avisos);
        return avisos;
    }

    /** True si algún aviso impide publicar. */
    public static boolean hayBloqueantes(List<Aviso> avisos) {
        return avisos.stream().anyMatch(Aviso::bloqueante);
    }

    private static void revisarConclusion(NodoLienzo nodo, List<Aviso> avisos) {
        if (nodo.texto() == null || nodo.texto().isBlank()) {
            avisos.add(new Aviso(Codigo.TEXTO_VACIO, true,
                    "Hay una conclusión sin mensaje para la cuidadora.", nodo.id(), null));
        }
        if (nodo.desenlace() == null) {
            avisos.add(new Aviso(Codigo.SIN_DESENLACE, true, "La conclusión \"" + nombre(nodo)
                    + "\" no dice si es una recomendación, una consulta o una urgencia.", nodo.id(), null));
        }
    }

    private static void revisarPregunta(DocumentoLienzo documento, NodoLienzo nodo, List<Aviso> avisos) {
        if (nodo.texto() == null || nodo.texto().isBlank()) {
            avisos.add(new Aviso(Codigo.TEXTO_VACIO, true, "Hay una pregunta sin texto.", nodo.id(), null));
        }
        if (nodo.respuestas().size() < MINIMO_RESPUESTAS) {
            avisos.add(new Aviso(Codigo.POCAS_RESPUESTAS, true, "\"" + nombre(nodo)
                    + "\" necesita al menos " + MINIMO_RESPUESTAS + " respuestas.", nodo.id(), null));
        }
        Set<String> textos = new HashSet<>();
        for (RespuestaLienzo r : nodo.respuestas()) {
            if (r.texto() == null || r.texto().isBlank()) {
                avisos.add(new Aviso(Codigo.TEXTO_VACIO, true,
                        "Una respuesta de \"" + nombre(nodo) + "\" no tiene texto.", nodo.id(), r.id()));
            } else if (!textos.add(EditorLienzo.normalizado(r.texto()))) {
                avisos.add(new Aviso(Codigo.RESPUESTA_REPETIDA, true, "\"" + nombre(nodo)
                        + "\" tiene dos veces la respuesta \"" + r.texto() + "\".", nodo.id(), r.id()));
            }
            if (documento.nodo(r.destino()).isEmpty()) {
                avisos.add(new Aviso(Codigo.RESPUESTA_SIN_CAMINO, true, "La respuesta \"" + textoDe(r)
                        + "\" de \"" + nombre(nodo) + "\" todavía no dice qué pasa después.", nodo.id(), r.id()));
            }
        }
    }

    /**
     * Busca, con un recorrido en profundidad desde la primera pregunta, las
     * respuestas que vuelven a una pregunta del camino actual. El editor ya
     * impide crearlas; esto protege documentos guardados por otra vía.
     */
    private static void buscarCiclos(DocumentoLienzo documento, List<Aviso> avisos) {
        Map<String, Integer> estado = new HashMap<>();
        if (documento.nodo(documento.inicio()).isPresent()) {
            visitar(documento, documento.inicio(), estado, avisos);
        }
    }

    /** 1 = en el camino actual, 2 = terminado. */
    private static void visitar(DocumentoLienzo documento, String id, Map<String, Integer> estado,
            List<Aviso> avisos) {
        estado.put(id, 1);
        NodoLienzo nodo = documento.nodos().get(id);
        for (RespuestaLienzo r : nodo.respuestas()) {
            NodoLienzo destino = r.destino() == null ? null : documento.nodos().get(r.destino());
            if (destino == null) {
                continue;
            }
            Integer visto = estado.get(destino.id());
            if (visto == null) {
                visitar(documento, destino.id(), estado, avisos);
            } else if (visto == 1) {
                avisos.add(new Aviso(Codigo.CICLO, true, "\"" + nombre(nodo) + "\" vuelve a \""
                        + nombre(destino) + "\": el recorrido no terminaría nunca.", nodo.id(), r.id()));
            }
        }
        estado.put(id, 2);
    }

    /** Texto del nodo para los mensajes, o una descripción si está vacío. */
    static String nombre(NodoLienzo nodo) {
        if (nodo.texto() != null && !nodo.texto().isBlank()) {
            return nodo.texto();
        }
        return nodo.tipo() == TipoNodo.PREGUNTA ? "una pregunta sin texto" : "una conclusión sin mensaje";
    }

    private static String textoDe(RespuestaLienzo r) {
        return r.texto() == null || r.texto().isBlank() ? "(sin texto)" : r.texto();
    }
}
