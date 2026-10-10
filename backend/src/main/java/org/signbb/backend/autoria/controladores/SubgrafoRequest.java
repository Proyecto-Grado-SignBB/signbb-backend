package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un subgrafo, para crearlo o cambiarlos después. {@code nombre} es
 * el nombre técnico, que solo ven los médicos; {@code puenteId} es una de las
 * categorías de {@code GET /api/autoria/categorias}; {@code etiquetaOpcion}
 * es el nombre para la familia, obligatorio: la opción, en palabras
 * sencillas, que verá la cuidadora en la pregunta puente.
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
