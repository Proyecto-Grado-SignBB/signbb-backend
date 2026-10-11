package org.signbb.backend.autoria.servicios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.media.FormatoImagen;

/**
 * Operaciones de edición del lienzo. Cada una recibe el documento y devuelve
 * uno nuevo; no tiene estado ni toca la base de datos.
 *
 * <p>
 * El editor solo rechaza lo que dejaría el lienzo en un estado que el médico
 * no puede corregir desde la pantalla: dos primeras preguntas, una conclusión
 * sin desenlace, respuestas repetidas, cambiar el tipo de un nodo o un enlace
 * que forma un círculo. Lo que está a medio hacer (una respuesta que sigue
 * siendo un "+", una pregunta con una sola respuesta, un texto vacío) se
 * permite y aparece en los avisos de {@link ValidadorLienzo}.
 *
 * <p>
 * Los ids los genera el editor con la {@code secuencia} del documento
 * ({@code pregunta_N}, {@code conclusion_N}, {@code r_N}) y no se reutilizan.
 */
public final class EditorLienzo {

    /**
     * Datos de un nodo tal como los manda el panel del lienzo.
     *
     * @param tipo             pregunta o conclusión; al editar puede ir
     *                         {@code null} para conservar el actual
     * @param texto            enunciado o mensaje para la cuidadora
     * @param variasRespuestas si se pueden marcar varias (solo preguntas)
     * @param desenlace        desenlace (solo conclusiones, obligatorio)
     * @param respuestas       respuestas de la pregunta
     * @param imagenes         nombres de las imágenes de la pregunta (máximo
     *                         3); {@code null} al editar conserva las actuales
     */
    public record DatosNodo(TipoNodo tipo, String texto, boolean variasRespuestas, Desenlace desenlace,
            List<DatosRespuesta> respuestas, List<String> imagenes) {

        /** Normaliza la lista de respuestas ausente a una vacía; {@code imagenes} nulo se respeta. */
        public DatosNodo {
            respuestas = respuestas == null ? List.of() : List.copyOf(respuestas);
            imagenes = imagenes == null ? null : Collections.unmodifiableList(new ArrayList<>(imagenes));
        }

        /** Datos sin tocar las imágenes. */
        public DatosNodo(TipoNodo tipo, String texto, boolean variasRespuestas, Desenlace desenlace,
                List<DatosRespuesta> respuestas) {
            this(tipo, texto, variasRespuestas, desenlace, respuestas, null);
        }
    }

    /**
     * Respuesta tal como la manda el panel.
     *
     * @param id       id de una respuesta existente (al editar, para
     *                 conservar su destino) o {@code null} si es nueva
     * @param texto    texto de la respuesta
     * @param gravedad semáforo; {@code null} es {@link Gravedad#LEVE}
     */
    public record DatosRespuesta(String id, String texto, Gravedad gravedad) {
    }

    private EditorLienzo() {
    }

    /**
     * La acción del "+": crea un nodo y, si se indica, lo conecta como
     * destino de una respuesta que todavía no tenía camino. Sin respuesta
     * ({@code respuestaId} nulo) crea la primera pregunta del lienzo.
     *
     * @throws EdicionInvalidaException     si ya hay primera pregunta, si la
     *                                      primera no es una pregunta o si la
     *                                      respuesta ya tiene camino
     * @throws RecursoNoEncontradoException si la respuesta no existe
     */
    public static DocumentoLienzo agregarNodo(DocumentoLienzo documento, String despuesDeNodo,
            String respuestaId, DatosNodo datos) {
        if (datos == null || datos.tipo() == null) {
            throw new EdicionInvalidaException("Elige si el nodo es una pregunta o una conclusión.");
        }
        Secuencia ids = new Secuencia(documento.secuencia());
        Map<String, NodoLienzo> nodos = new LinkedHashMap<>(documento.nodos());
        String inicio = documento.inicio();

        String id = ids.nuevo(datos.tipo() == TipoNodo.PREGUNTA ? "pregunta" : "conclusion");
        if (respuestaId == null) {
            if (inicio != null) {
                throw new EdicionInvalidaException(
                        "El subgrafo ya tiene una primera pregunta: agrega lo siguiente desde el \"+\" de una respuesta.");
            }
            if (datos.tipo() != TipoNodo.PREGUNTA) {
                throw new EdicionInvalidaException("El subgrafo tiene que empezar con una pregunta.");
            }
            inicio = id;
        } else {
            NodoLienzo origen = duenoDe(documento, respuestaId);
            if (despuesDeNodo != null && !despuesDeNodo.equals(origen.id())) {
                throw new RecursoNoEncontradoException(
                        "la respuesta '" + respuestaId + "' no es de '" + despuesDeNodo + "'");
            }
            RespuestaLienzo respuesta = respuestaDe(origen, respuestaId);
            if (respuesta.destino() != null) {
                throw new EdicionInvalidaException("La respuesta \"" + respuesta.texto()
                        + "\" ya tiene un camino. Elimina el nodo al que lleva o cámbialo por otra pregunta.");
            }
            nodos.put(origen.id(), conDestino(origen, respuestaId, id));
        }
        nodos.put(id, construir(id, datos.tipo(), datos, Map.of(), List.of(), ids));
        return new DocumentoLienzo(inicio, ids.valor(), nodos);
    }

    /**
     * La opción "Pregunta existente": hace que una respuesta lleve a una
     * pregunta que ya está en el lienzo. Con {@code nodoId} nulo la respuesta
     * vuelve a ser un "+"; lo que colgaba de ella queda suelto (no se borra).
     *
     * @throws EdicionInvalidaException     si el destino no es una pregunta,
     *                                      es la misma pregunta o el enlace
     *                                      forma un círculo
     * @throws RecursoNoEncontradoException si la respuesta o el nodo no existen
     */
    public static DocumentoLienzo enlazar(DocumentoLienzo documento, String respuestaId, String nodoId) {
        NodoLienzo origen = duenoDe(documento, respuestaId);
        if (nodoId != null) {
            NodoLienzo destino = nodoExistente(documento, nodoId);
            if (!destino.esPregunta()) {
                throw new EdicionInvalidaException("Una respuesta solo puede ir a una pregunta que ya existe.");
            }
            if (destino.id().equals(origen.id())) {
                throw new EdicionInvalidaException("Una respuesta no puede volver a su misma pregunta.");
            }
            if (Alcance.desde(documento, destino.id()).contains(origen.id())) {
                throw new EdicionInvalidaException("\"" + destino.texto() + "\" ya lleva a \"" + origen.texto()
                        + "\": este enlace haría un círculo y el recorrido no terminaría.");
            }
        }
        Map<String, NodoLienzo> nodos = new LinkedHashMap<>(documento.nodos());
        nodos.put(origen.id(), conDestino(origen, respuestaId, nodoId));
        return new DocumentoLienzo(documento.inicio(), documento.secuencia(), nodos);
    }

    /**
     * Cambia el texto, el desenlace o las respuestas de un nodo. Las
     * respuestas que vienen con id conservan su destino; las que vienen sin
     * id son nuevas ("+"); las que ya no vienen se quitan y lo que colgaba de
     * ellas queda suelto. El tipo del nodo no cambia.
     *
     * @throws EdicionInvalidaException     si se pide cambiar el tipo, si una
     *                                      respuesta no es de este nodo o si
     *                                      los datos no son válidos
     * @throws RecursoNoEncontradoException si el nodo no existe
     */
    public static DocumentoLienzo editarNodo(DocumentoLienzo documento, String nodoId, DatosNodo datos) {
        NodoLienzo actual = nodoExistente(documento, nodoId);
        if (datos == null) {
            throw new EdicionInvalidaException("Faltan los datos del nodo.");
        }
        if (datos.tipo() != null && datos.tipo() != actual.tipo()) {
            throw new EdicionInvalidaException(
                    "No se puede cambiar una pregunta por una conclusión (ni al revés): elimina el nodo y agrega otro.");
        }
        Map<String, RespuestaLienzo> previas = new LinkedHashMap<>();
        actual.respuestas().forEach(r -> previas.put(r.id(), r));

        Secuencia ids = new Secuencia(documento.secuencia());
        Map<String, NodoLienzo> nodos = new LinkedHashMap<>(documento.nodos());
        nodos.put(nodoId, construir(nodoId, actual.tipo(), datos, previas, actual.imagenes(), ids));
        return new DocumentoLienzo(documento.inicio(), ids.valor(), nodos);
    }

    /**
     * Elimina un nodo. Las respuestas que llevaban a él vuelven a ser un "+"
     * y se eliminan también los nodos a los que solo se llegaba a través de
     * él (su rama). Los nodos que ya estaban sueltos se conservan. Eliminar la
     * primera pregunta deja el lienzo sin inicio.
     *
     * @throws RecursoNoEncontradoException si el nodo no existe
     */
    public static DocumentoLienzo eliminarNodo(DocumentoLienzo documento, String nodoId) {
        nodoExistente(documento, nodoId);
        Set<String> antes = Alcance.desdeInicio(documento);

        Map<String, NodoLienzo> nodos = new LinkedHashMap<>();
        for (NodoLienzo nodo : documento.nodos().values()) {
            if (!nodo.id().equals(nodoId)) {
                nodos.put(nodo.id(), sinDestino(nodo, nodoId));
            }
        }
        String inicio = nodoId.equals(documento.inicio()) ? null : documento.inicio();
        DocumentoLienzo sinNodo = new DocumentoLienzo(inicio, documento.secuencia(), nodos);

        Set<String> despues = Alcance.desdeInicio(sinNodo);
        Set<String> rama = new HashSet<>(antes);
        rama.removeAll(despues);
        if (rama.isEmpty()) {
            return sinNodo;
        }
        Map<String, NodoLienzo> podados = new LinkedHashMap<>();
        for (NodoLienzo nodo : nodos.values()) {
            if (!rama.contains(nodo.id())) {
                NodoLienzo limpio = nodo;
                for (String quitado : rama) {
                    limpio = sinDestino(limpio, quitado);
                }
                podados.put(nodo.id(), limpio);
            }
        }
        return new DocumentoLienzo(inicio, documento.secuencia(), podados);
    }

    /** Arma el nodo validando los datos según su tipo. */
    private static NodoLienzo construir(String id, TipoNodo tipo, DatosNodo datos,
            Map<String, RespuestaLienzo> previas, List<String> imagenesActuales, Secuencia ids) {
        String texto = limpio(datos.texto());
        if (tipo == TipoNodo.CONCLUSION) {
            if (datos.desenlace() == null) {
                throw new EdicionInvalidaException(
                        "Elige si la conclusión es una recomendación, una consulta con el médico o ir a urgencias.");
            }
            if (!datos.respuestas().isEmpty()) {
                throw new EdicionInvalidaException("Una conclusión cierra el recorrido: no lleva respuestas.");
            }
            if (datos.imagenes() != null && !datos.imagenes().isEmpty()) {
                throw new EdicionInvalidaException("Las imágenes son solo para preguntas.");
            }
            return new NodoLienzo(id, tipo, texto, false, datos.desenlace(), List.of());
        }

        List<RespuestaLienzo> respuestas = new ArrayList<>();
        Set<String> textos = new HashSet<>();
        Set<String> idsUsados = new HashSet<>();
        for (DatosRespuesta d : datos.respuestas()) {
            if (d == null) {
                continue;
            }
            String textoRespuesta = limpio(d.texto());
            if (!textoRespuesta.isEmpty() && !textos.add(normalizado(textoRespuesta))) {
                throw new EdicionInvalidaException("La respuesta \"" + textoRespuesta + "\" está repetida.");
            }
            String destino = null;
            String idRespuesta;
            if (d.id() == null) {
                idRespuesta = ids.nuevo("r");
            } else {
                RespuestaLienzo previa = previas.get(d.id());
                if (previa == null || !idsUsados.add(d.id())) {
                    throw new EdicionInvalidaException("La respuesta '" + d.id() + "' no es de esta pregunta.");
                }
                idRespuesta = previa.id();
                destino = previa.destino();
            }
            respuestas.add(new RespuestaLienzo(idRespuesta, textoRespuesta, d.gravedad(), destino));
        }
        List<String> imagenes = datos.imagenes() == null ? imagenesActuales : imagenesValidas(datos.imagenes());
        return new NodoLienzo(id, tipo, texto, datos.variasRespuestas(), null, respuestas, imagenes);
    }

    /** Máximo de imágenes por pregunta; sin repetidas y con nombres que dio el servidor al subirlas. */
    private static List<String> imagenesValidas(List<String> imagenes) {
        if (imagenes.size() > FormatoImagen.MAXIMO_POR_PREGUNTA) {
            throw new EdicionInvalidaException(
                    "Una pregunta puede tener hasta " + FormatoImagen.MAXIMO_POR_PREGUNTA + " imágenes.");
        }
        Set<String> vistas = new HashSet<>();
        for (String nombre : imagenes) {
            if (!FormatoImagen.nombreValido(nombre)) {
                throw new EdicionInvalidaException("Una de las imágenes no es válida: súbela de nuevo.");
            }
            if (!vistas.add(nombre)) {
                throw new EdicionInvalidaException("La misma imagen está repetida en la pregunta.");
            }
        }
        return List.copyOf(imagenes);
    }

    /** Nodo dueño de una respuesta. */
    private static NodoLienzo duenoDe(DocumentoLienzo documento, String respuestaId) {
        for (NodoLienzo nodo : documento.nodos().values()) {
            for (RespuestaLienzo r : nodo.respuestas()) {
                if (r.id().equals(respuestaId)) {
                    return nodo;
                }
            }
        }
        throw new RecursoNoEncontradoException("no hay una respuesta '" + respuestaId + "' en el lienzo");
    }

    private static RespuestaLienzo respuestaDe(NodoLienzo nodo, String respuestaId) {
        return nodo.respuestas().stream().filter(r -> r.id().equals(respuestaId)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "la respuesta '" + respuestaId + "' no es de '" + nodo.id() + "'"));
    }

    private static NodoLienzo nodoExistente(DocumentoLienzo documento, String nodoId) {
        return documento.nodo(nodoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay un nodo '" + nodoId + "' en el lienzo"));
    }

    /** Copia del nodo con la respuesta indicada apuntando a {@code destino}. */
    private static NodoLienzo conDestino(NodoLienzo nodo, String respuestaId, String destino) {
        return nodo.conRespuestas(nodo.respuestas().stream()
                .map(r -> r.id().equals(respuestaId) ? r.conDestino(destino) : r)
                .toList());
    }

    /** Copia del nodo con las respuestas que iban a {@code destino} convertidas en "+". */
    private static NodoLienzo sinDestino(NodoLienzo nodo, String destino) {
        if (nodo.respuestas().stream().noneMatch(r -> destino.equals(r.destino()))) {
            return nodo;
        }
        return nodo.conRespuestas(nodo.respuestas().stream()
                .map(r -> destino.equals(r.destino()) ? r.conDestino(null) : r)
                .toList());
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.strip();
    }

    /** Forma de comparar textos de respuestas: sin mayúsculas ni espacios repetidos. */
    static String normalizado(String texto) {
        return texto.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Contador de ids del documento. */
    private static final class Secuencia {
        private int valor;

        Secuencia(int inicial) {
            this.valor = inicial;
        }

        String nuevo(String prefijo) {
            valor++;
            return prefijo + "_" + valor;
        }

        int valor() {
            return valor;
        }
    }
}
