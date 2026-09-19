package org.signbb.backend.observaciones.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cuerpo de la petición que responde la pregunta actual. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaRequest {
    /** Valor de la respuesta elegida. */
    private String respuesta;
}
