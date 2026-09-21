package org.signbb.backend.reglas.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;

/**
 * TAD ReglaClinica (Sección 4.2 del documento de diseño).
 *
 * <p>
 * Representa el conocimiento del neonatólogo: una {@link ExpresionCondicion}
 * (condiciones combinadas con AND y/o OR, anidables) que, al satisfacerse en un
 * {@link EstadoPaciente}, apunta a un nodo destino (alerta, recomendación, etc.)
 * con un nivel de gravedad asociado. No decide qué hacer con el resultado — solo
 * sabe "compararse contra un estado". La identidad está dada por {@code idRegla}.
 *
 * <p>
 * Migrado desde {@code src/regla_clinica.py}. El constructor histórico que
 * recibe una lista plana de condiciones se conserva y equivale a un
 * {@link ExpresionCondicion.Y} (AND) de esas condiciones.
 */
public final class ReglaClinica {

    private final String idRegla;
    private final String nombre;
    private final ExpresionCondicion expresion;
    private final List<Condicion> condiciones;
    private final Nodo nodoDestino;
    private final NivelAlerta nivel;

    /**
     * Crea la regla a partir de una lista plana de condiciones unidas por AND
     * (compatibilidad con el diseño previo).
     */
    public ReglaClinica(String idRegla, String nombre, List<Condicion> condiciones,
            Nodo nodoDestino, NivelAlerta nivel) {
        this(idRegla, nombre, comoExpresion(condiciones), nodoDestino, nivel);
    }

    /**
     * Crea la regla con una {@link ExpresionCondicion} arbitraria (AND/OR
     * anidados) validando sus cinco componentes.
     */
    public ReglaClinica(String idRegla, String nombre, ExpresionCondicion expresion,
            Nodo nodoDestino, NivelAlerta nivel) {
        if (idRegla == null || idRegla.isEmpty()) {
            throw new IllegalArgumentException("idRegla debe ser una cadena no vacía");
        }
        if (nombre == null) {
            throw new IllegalArgumentException("nombre debe ser una cadena");
        }
        if (expresion == null) {
            throw new IllegalArgumentException("expresion debe ser una ExpresionCondicion");
        }
        List<Condicion> hojas = expresion.hojas();
        if (hojas.isEmpty()) {
            throw new IllegalArgumentException(
                    "la expresión debe tener al menos una condición");
        }
        for (Condicion c : hojas) {
            if (c.idNodo() == null || c.respuesta() == null) {
                throw new IllegalArgumentException(
                        "cada condición debe ser (idNodo, respuesta) no nulos");
            }
        }
        if (nodoDestino == null) {
            throw new IllegalArgumentException("nodoDestino debe ser Nodo");
        }
        if (nivel == null) {
            throw new IllegalArgumentException("nivel debe ser NivelAlerta");
        }
        this.idRegla = idRegla;
        this.nombre = nombre;
        this.expresion = expresion;
        this.condiciones = hojas;
        this.nodoDestino = nodoDestino;
        this.nivel = nivel;
    }

    /** Envuelve una lista plana de condiciones en un AND (compatibilidad). */
    private static ExpresionCondicion comoExpresion(List<Condicion> condiciones) {
        if (condiciones == null || condiciones.isEmpty()) {
            throw new IllegalArgumentException(
                    "condiciones debe ser una lista no vacía de pares");
        }
        for (Condicion c : condiciones) {
            if (c == null || c.idNodo() == null || c.respuesta() == null) {
                throw new IllegalArgumentException(
                        "cada condición debe ser (idNodo, respuesta) no nulos");
            }
        }
        return new ExpresionCondicion.Y(new ArrayList<>(condiciones));
    }

    /** Identificador único de la regla. */
    public String getIdRegla() {
        return idRegla;
    }

    /** Texto descriptivo de la regla. */
    public String getNombre() {
        return nombre;
    }

    /**
     * Copia de las condiciones hoja, en orden (no expone la estructura interna).
     * Para una regla creada con AND/OR anidados, devuelve las condiciones
     * aplanadas; la lógica de combinación vive en {@link #getExpresion()}.
     */
    public List<Condicion> getCondiciones() {
        return new ArrayList<>(condiciones);
    }

    /** Expresión lógica raíz (AND/OR anidados) que define cuándo se activa. */
    public ExpresionCondicion getExpresion() {
        return expresion;
    }

    /** Nodo terminal al que salta el motor si la regla se activa. */
    public Nodo getNodoDestino() {
        return nodoDestino;
    }

    /** Severidad producida al activarse la regla. */
    public NivelAlerta getNivel() {
        return nivel;
    }

    /** True si la expresión de condiciones (AND/OR) se satisface en el estado. */
    public boolean condicionesCumplidas(EstadoPaciente estado) {
        return expresion.evaluar(estado);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReglaClinica)) {
            return false;
        }
        return idRegla.equals(((ReglaClinica) o).idRegla);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idRegla);
    }

    @Override
    public String toString() {
        return "ReglaClinica(id='" + idRegla + "', nombre='" + nombre + "', condiciones="
                + condiciones.size() + ", destino='" + nodoDestino.getId() + "', nivel="
                + nivel + ")";
    }
}