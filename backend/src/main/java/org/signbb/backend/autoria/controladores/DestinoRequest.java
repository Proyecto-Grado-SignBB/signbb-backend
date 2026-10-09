package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pregunta existente a la que pasa a llevar una respuesta; {@code null} la vuelve un "+". */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DestinoRequest {
    private String nodoId;
}
