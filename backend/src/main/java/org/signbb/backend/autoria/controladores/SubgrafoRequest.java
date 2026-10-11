package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

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
    @Schema(description = "Nombre técnico, que solo ven los médicos")
    private String nombre;

    @Schema(description = "Una de las categorías de GET /api/autoria/categorias")
    private String puenteId;

    @Schema(description = "Nombre para la familia, obligatorio: lo que verá la cuidadora", requiredMode = Schema.RequiredMode.REQUIRED)
    private String etiquetaOpcion;

    @Schema(description = "Autor del contenido")
    private String autor;

    @Schema(description = "Institución del autor")
    private String institucion;
}
