package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Respuesta del lienzo después de la cual se agrega un nodo. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DespuesDeRequest {
    private String nodoId;
    private String respuestaId;
}
