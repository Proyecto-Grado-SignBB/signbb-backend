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
 * Representa el conocimiento del neonatólogo como una implicación cuya premisa
 * es una fórmula booleana en <strong>forma normal disyuntiva</strong> (FND):
 * una lista de {@link Caso} (conjunciones) unidas por disyunción. Al
 * satisfacerse en un {@link EstadoPaciente} apunta a un nodo destino (alerta,
 * recomendación, etc.) con un nivel de gravedad asociado. No decide qué hacer
 * con el resultado — solo sabe "compararse contra un estado". La identidad está
 * dada por {@code idRegla}.
 *
 * <p>
 * El constructor histórico que recibe una lista plana de condiciones se
 * conserva y equivale a un único {@link Caso} (AND de esas condiciones).
 */
public final class ReglaClinica {

    private final String idRegla;
    private final String nombre;
    private final List<Caso> casos;
    private final Nodo nodoDestino;
    private final NivelAlerta nivel;

    /**
     * Crea la regla a partir de una lista plana de condiciones unidas por AND
     * (equivale a un único caso de la FND).
     */
    public ReglaClinica(String idRegla, String nombre, List<Condicion> condiciones,
            Nodo nodoDestino, NivelAlerta nivel) {
        this(idRegla, nombre, new Caso[] { new Caso(condiciones) }, nodoDestino, nivel);
    }

    /**
     * Crea la regla con una FND arbitraria (OR de casos, cada caso un AND de
     * condiciones) validando sus cinco componentes.
     */
    public ReglaClinica(String idRegla, String nombre, Caso[] casos,
            Nodo nodoDestino, NivelAlerta nivel) {
        if (idRegla == null || idRegla.isEmpty()) {
            throw new IllegalArgumentException("idRegla debe ser una cadena no vacía");
        }
        if (nombre == null) {
            throw new IllegalArgumentException("nombre debe ser una cadena");
        }
        if (casos == null || casos.length == 0) {
            throw new IllegalArgumentException("la regla requiere al menos un caso");
        }
        for (Caso caso : casos) {
            if (caso == null) {
                throw new IllegalArgumentException("los casos no pueden ser nulos");
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
        this.casos = List.of(casos);
        this.nodoDestino = nodoDestino;
        this.nivel = nivel;
    }

    /** Crea una regla a partir de una lista de casos (FND). */
    public static ReglaClinica conCasos(String idRegla, String nombre, List<Caso> casos,
            Nodo nodoDestino, NivelAlerta nivel) {
        if (casos == null) {
            throw new IllegalArgumentException("casos debe ser una lista de Caso");
        }
        return new ReglaClinica(idRegla, nombre, casos.toArray(Caso[]::new), nodoDestino, nivel);
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
     * Copia de las condiciones hoja, aplanadas en orden a lo largo de todos
     * los casos. La estructura lógica de combinación (OR entre casos, AND
     * dentro de cada caso) vive en {@link #getCasos()}.
     */
    public List<Condicion> getCondiciones() {
        List<Condicion> hojas = new ArrayList<>();
        for (Caso caso : casos) {
            hojas.addAll(caso.condiciones());
        }
        return hojas;
    }

    /** Copia de los casos (conjunciones) que forman la FND de la regla. */
    public List<Caso> getCasos() {
        return new ArrayList<>(casos);
    }

    /** Nodo terminal al que salta el motor si la regla se activa. */
    public Nodo getNodoDestino() {
        return nodoDestino;
    }

    /** Severidad producida al activarse la regla. */
    public NivelAlerta getNivel() {
        return nivel;
    }

    /**
     * True si la FND de la regla se satisface en el estado: al menos un
     * {@link Caso} (OR) tiene todas sus condiciones cumplidas (AND).
     */
    public boolean condicionesCumplidas(EstadoPaciente estado) {
        for (Caso caso : casos) {
            if (caso.evaluar(estado)) {
                return true;
            }
        }
        return false;
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
        return "ReglaClinica(id='" + idRegla + "', nombre='" + nombre + "', casos="
                + casos.size() + ", destino='" + nodoDestino.getId() + "', nivel="
                + nivel + ")";
    }
}
