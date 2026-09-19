package org.signbb.backend.navegacion.modelo;

/**
 * Estado persistente de un recorrido de triaje entre peticiones HTTP.
 *
 * <p>
 * Guarda el {@link EstadoPaciente} con las respuestas acumuladas (que el
 * encadenamiento hacia adelante necesita completas) y el {@link ResultadoPaso}
 * más reciente, cuya {@code preguntaActual} es el nodo que espera respuesta.
 */
public class SesionTriaje {

    private final String id;
    private final EstadoPaciente estado;
    private ResultadoPaso ultimoPaso;

    /** Crea la sesión con su id, su estado y el paso inicial del recorrido. */
    public SesionTriaje(String id, EstadoPaciente estado, ResultadoPaso ultimoPaso) {
        this.id = id;
        this.estado = estado;
        this.ultimoPaso = ultimoPaso;
    }

    /** Identificador único de la sesión (UUID). */
    public String getId() {
        return id;
    }

    /** Respuestas acumuladas del cuidador en esta sesión. */
    public EstadoPaciente getEstado() {
        return estado;
    }

    /** Último resultado producido por el motor (pregunta pendiente o desenlace). */
    public ResultadoPaso getUltimoPaso() {
        return ultimoPaso;
    }

    /** Actualiza el último resultado tras avanzar el recorrido. */
    public void setUltimoPaso(ResultadoPaso ultimoPaso) {
        this.ultimoPaso = ultimoPaso;
    }
}
