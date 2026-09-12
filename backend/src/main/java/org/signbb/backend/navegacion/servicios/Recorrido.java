package org.signbb.backend.navegacion.servicios;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entidad JPA de la sesión de recorrido de un cuidador por un grafo. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Recorrido {
    /** Identificador autogenerado. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JsonProperty("recorridoId")
    private long id;

    /** Nodo actual del recorrido. */
    @ManyToOne
    private Nodo nodoActual;

    /** Mensaje/diagnóstico mostrado al cuidador. */
    private String mensaje;

    /** Estado de avance del recorrido. */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Estado {
        /** Pendiente de más respuestas. */
        EN_CURSO,
        /** Recorrido terminado. */
        FINALIZADO
    }

    /**
     * Resultado del recorrido; provisional, pendiente de alinear con
     * {@link org.signbb.backend.model.NivelAlerta}.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Resultado {
        /** Sin señales de alarma. */
        NORMAL,
        /** Requiere valoración médica. */
        REQUIERE_ATENCION
    }

    /** Estado de avance. */
    @Enumerated(EnumType.STRING)
    private Estado estado;

    /** Resultado final; null mientras está en curso. */
    @Enumerated(EnumType.STRING)
    private Resultado resultado;
}
