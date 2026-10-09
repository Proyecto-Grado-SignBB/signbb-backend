package org.signbb.backend.autoria.controladores;

import org.signbb.backend.autoria.modelo.Gravedad;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Respuesta de una pregunta; {@code id} solo al editar una que ya existe. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpcionRequest {
    private String id;
    private String texto;
    private Gravedad gravedad;
}
