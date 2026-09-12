package org.signbb.backend.navegacion.servicios;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Opción de respuesta embebida en un nodo: texto visible y valor interno. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpcionRespuesta {
    /** Texto visible de la opción. */
    private String texto;

    /** Valor interno de la opción. */
    private String valor;
}
