package org.signbb.backend.navegacion.modelo;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TAD Nodo del Grafo Clínico (Sección 2 del documento de diseño).
 *
 * <p>
 * Representa una pregunta al padre o un nodo terminal. En el TAD el tipo de un
 * terminal es directamente su desenlace ({@code recomendacion},
 * {@code consulta_medica} o {@code urgencia}); el documento JSON lo expresa
 * como {@code tipo: "terminal"} más {@code desenlace} y
 * {@code GrafoClinicoMapper} hace la traducción. La identidad de un Nodo está
 * dada únicamente por su {@code id}: dos nodos con el mismo id se consideran
 * iguales.
 *
 * <p>
 * Migrado desde {@code src/nodo.py}.
 */
public final class Nodo {

    /**
     * Tipos de nodo admitidos: {@code pregunta} y los tres desenlaces de un
     * terminal ({@code recomendacion}, {@code consulta_medica},
     * {@code urgencia}).
     */
    public static final Set<String> TIPOS_VALIDOS = Set.of("pregunta", "recomendacion", "consulta_medica",
            "urgencia");

    /**
     * MODIFICADO: valores válidos para el override opcional de
     * {@code tipoInteraccion} en la metadata del grafo. Cuando es
     * {@code null}, {@code GrafoClinico.tipoInteraccion} lo infiere a partir de las
     * opciones del nodo.
     */
    public static final Set<String> TIPOS_INTERACCION_VALIDOS = Set.of("SI_NO", "SELECCION_UNICA",
            "SELECCION_MULTIPLE");

    private final String id;
    private final String tipo;
    private final String contenido;
    private final int peso;
    private final boolean archivos;
    private final List<String> src;
    private final String tipoInteraccion;

    /** Constructor con peso por defecto (0) y sin archivos adjuntos. */
    public Nodo(String id, String tipo, String contenido) {
        this(id, tipo, contenido, 0);
    }

    /** Constructor con peso explícito y sin archivos adjuntos. */
    public Nodo(String id, String tipo, String contenido, int peso) {
        this(id, tipo, contenido, peso, false, List.of());
    }

    /**
     * Constructor completo: permite marcar el nodo como una pregunta con
     * imágenes de apoyo ({@code archivos}) y sus rutas ({@code src}). Sin
     * override explícito de {@code tipoInteraccion}.
     */
    public Nodo(String id, String tipo, String contenido, int peso, boolean archivos, List<String> src) {
        this(id, tipo, contenido, peso, archivos, src, null);
    }

    /**
     * MODIFICADO: constructor completo con override explícito de
     * {@code tipoInteraccion} (nulo si el grafo no lo especifica para este
     * nodo).
     */
    public Nodo(String id, String tipo, String contenido, int peso, boolean archivos, List<String> src,
            String tipoInteraccion) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id debe ser una cadena no vacía");
        }
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new IllegalArgumentException(
                    "tipo inválido: '" + tipo + "'. Debe ser uno de " + TIPOS_VALIDOS);
        }
        if (contenido == null) {
            throw new IllegalArgumentException("contenido debe ser una cadena");
        }
        if (tipoInteraccion != null && !TIPOS_INTERACCION_VALIDOS.contains(tipoInteraccion)) {
            throw new IllegalArgumentException(
                    "tipoInteraccion inválido: '" + tipoInteraccion + "'. Debe ser uno de "
                            + TIPOS_INTERACCION_VALIDOS + " o venir ausente");
        }
        this.id = id;
        this.tipo = tipo;
        this.contenido = contenido;
        this.peso = peso;
        this.archivos = archivos;
        this.src = src == null ? List.of() : List.copyOf(src);
        this.tipoInteraccion = tipoInteraccion;
    }

    /** Id único del nodo. */
    public String getId() {
        return id;
    }

    /**
     * Tipo del nodo: {@code pregunta}, {@code recomendacion},
     * {@code consulta_medica} o {@code urgencia}.
     */
    public String getTipo() {
        return tipo;
    }

    /** Texto mostrado al cuidador. */
    public String getContenido() {
        return contenido;
    }

    /**
     * Peso de urgencia del nodo. En el formato de intercambio va de 1 a 3
     * (1 = baja, 2 = media, 3 = urgente); la escala se valida en
     * {@code GrafoClinicoMapper}, no en el TAD.
     */
    public int getPeso() {
        return peso;
    }

    /** True si la pregunta tiene imágenes de apoyo asociadas ({@code src}). */
    public boolean tieneArchivos() {
        return archivos;
    }

    /**
     * Rutas de las imágenes de apoyo (vacía si {@link #tieneArchivos()} es false).
     */
    public List<String> getSrc() {
        return src;
    }

    /**
     * MODIFICADO: override explícito de {@code tipoInteraccion} declarado en
     * la metadata del grafo para este nodo ({@code SI_NO},
     * {@code SELECCION_UNICA} o {@code SELECCION_MULTIPLE}), o {@code null}
     * si el grafo no lo especifica (el valor por defecto se infiere en
     * {@code GrafoClinico#tipoInteraccion} a partir de las opciones del nodo).
     */
    public String getTipoInteraccion() {
        return tipoInteraccion;
    }

    /** Un nodo es terminal si no es una pregunta. */
    public boolean esTerminal() {
        return !"pregunta".equals(tipo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Nodo)) {
            return false;
        }
        return id.equals(((Nodo) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Nodo(id='" + id + "', tipo='" + tipo + "', peso=" + peso + ")";
    }
}