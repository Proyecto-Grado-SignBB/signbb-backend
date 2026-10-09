package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un subgrafo, para crearlo o cambiarlos después. {@code puenteId}
 * es una de las categorías de {@code GET /api/autoria/categorias}; si
 * {@code etiquetaOpcion} viene vacía se usa el nombre.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubgrafoRequest {
    private String nombre;
    private String puenteId;
    private String etiquetaOpcion;
    private String autor;
    private String institucion;
}
