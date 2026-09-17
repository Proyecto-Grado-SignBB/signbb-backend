package org.signbb.backend.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TAD Nodo del Grafo Clínico (Sección 2 del documento de diseño).
 *
 * <p>
 * Representa una pregunta al padre o un nodo terminal (recomendación,
 * bloqueo, consulta médica). La identidad de un Nodo está dada únicamente por
 * su {@code id}: dos nodos con el mismo id se consideran iguales.
 *
 * <p>
 * Migrado desde {@code src/nodo.py}.
 */
public final class Nodo {

    /** Tipos de nodo admitidos. */
    public static final Set<String> TIPOS_VALIDOS = Set.of("pregunta", "recomendacion", "bloqueo", "consulta_medica");

    private final String id;
    private final String tipo;
    private final String contenido;
    private final int peso;
    private final boolean archivos;
    private final List<String> src;

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
     * imágenes de apoyo ({@code archivos}) y sus rutas ({@code src}).
     */
    public Nodo(String id, String tipo, String contenido, int peso, boolean archivos, List<String> src) {
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
        this.id = id;
        this.tipo = tipo;
        this.contenido = contenido;
        this.peso = peso;
        this.archivos = archivos;
        this.src = src == null ? List.of() : List.copyOf(src);
    }

    /** Id único del nodo. */
    public String getId() {
        return id;
    }

    /**
     * Tipo del nodo: {@code pregunta}, {@code recomendacion}, {@code bloqueo}
     * o {@code consulta_medica}.
     */
    public String getTipo() {
        return tipo;
    }

    /** Texto mostrado al cuidador. */
    public String getContenido() {
        return contenido;
    }

    /** Peso de urgencia del nodo. */
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