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
    private final Long versionId;
    private ResultadoPaso ultimoPaso;

    /** Crea la sesión con su id, su estado y el paso inicial; sin versión (usa la vigente). */
    public SesionTriaje(String id, EstadoPaciente estado, ResultadoPaso ultimoPaso) {
        this(id, estado, ultimoPaso, null);
    }

    /** Crea la sesión atada a la versión del contenido con la que empezó el recorrido. */
    public SesionTriaje(String id, EstadoPaciente estado, ResultadoPaso ultimoPaso, Long versionId) {
        this.id = id;
        this.estado = estado;
        this.ultimoPaso = ultimoPaso;
        this.versionId = versionId;
    }

    /**
     * Versión del contenido clínico con la que empezó el recorrido, que sigue
     * hasta terminar. {@code null} en sesiones guardadas antes de existir las
     * versiones: usan la vigente.
     */
    public Long getVersionId() {
        return versionId;
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
