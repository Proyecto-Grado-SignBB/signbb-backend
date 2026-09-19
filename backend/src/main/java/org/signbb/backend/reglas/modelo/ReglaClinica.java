package org.signbb.backend.reglas.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;

/**
 * TAD ReglaClinica (Sección 4.2 del documento de diseño).
 *
 * <p>
 * Representa el conocimiento del neonatólogo: un conjunto de condiciones
 * (unidas por AND) que, al cumplirse en un {@link EstadoPaciente}, apuntan a un
 * nodo destino (alerta, recomendación, etc.) con un nivel de gravedad asociado.
 * No decide qué hacer con el resultado — solo sabe "compararse contra un
 * estado". La identidad está dada por {@code idRegla}.
 *
 * <p>
 * Migrado desde {@code src/regla_clinica.py}.
 */
public final class ReglaClinica {

    private final String idRegla;
    private final String nombre;
    private final List<Condicion> condiciones;
    private final Nodo nodoDestino;
    private final NivelAlerta nivel;

    /** Crea la regla validando sus cinco componentes. */
    public ReglaClinica(String idRegla, String nombre, List<Condicion> condiciones,
            Nodo nodoDestino, NivelAlerta nivel) {
        if (idRegla == null || idRegla.isEmpty()) {
            throw new IllegalArgumentException("idRegla debe ser una cadena no vacía");
        }
        if (nombre == null) {
            throw new IllegalArgumentException("nombre debe ser una cadena");
        }
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
        if (nodoDestino == null) {
            throw new IllegalArgumentException("nodoDestino debe ser Nodo");
        }
        if (nivel == null) {
            throw new IllegalArgumentException("nivel debe ser NivelAlerta");
        }
        this.idRegla = idRegla;
        this.nombre = nombre;
        this.condiciones = new ArrayList<>(condiciones);
        this.nodoDestino = nodoDestino;
        this.nivel = nivel;
    }

    /** Identificador único de la regla. */
    public String getIdRegla() {
        return idRegla;
    }

    /** Texto descriptivo de la regla. */
    public String getNombre() {
        return nombre;
    }

    /** Copia de las condiciones (no expone la lista interna). */
    public List<Condicion> getCondiciones() {
        return new ArrayList<>(condiciones);
    }

    /** Nodo terminal al que salta el motor si la regla se activa. */
    public Nodo getNodoDestino() {
        return nodoDestino;
    }

    /** Severidad producida al activarse la regla. */
    public NivelAlerta getNivel() {
        return nivel;
    }

    /** True si TODAS las condiciones se cumplen en el estado dado. */
    public boolean condicionesCumplidas(EstadoPaciente estado) {
        for (Condicion c : condiciones) {
            Optional<String> actual = estado.getRespuesta(c.idNodo());
            if (actual.isEmpty() || !actual.get().equals(c.respuesta())) {
                return false;
            }
        }
        return true;
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