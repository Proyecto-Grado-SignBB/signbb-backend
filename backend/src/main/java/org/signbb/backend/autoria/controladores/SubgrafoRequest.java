package org.signbb.backend.autoria.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.signbb.backend.config.PatronesTexto;

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
    @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
    @Pattern(regexp = PatronesTexto.UNA_LINEA, message = "El nombre no puede tener saltos de línea.")
    private String nombre;

    @Schema(description = "Una de las categorías de GET /api/autoria/categorias")
    @Size(max = 100, message = "La categoría no es válida (demasiado larga).")
    private String puenteId;

    @Schema(description = "Nombre para la familia, obligatorio: lo que verá la cuidadora", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 100, message = "El nombre para la familia no puede pasar de 100 caracteres.")
    @Pattern(regexp = PatronesTexto.UNA_LINEA, message = "El nombre para la familia no puede tener saltos de línea.")
    private String etiquetaOpcion;

    /** Obsoleto: se ignora; el autor sale de la cuenta que hace la petición. */
    @Schema(description = "Obsoleto: se ignora. El autor sale de la cuenta que hace la petición",
            deprecated = true)
    @Size(max = 150, message = "El autor no puede pasar de 150 caracteres.")
    private String autor;

    /** Obsoleto: se ignora; la institución sale de la cuenta que hace la petición. */
    @Schema(description = "Obsoleto: se ignora. La institución sale de la cuenta que hace la petición",
            deprecated = true)
    @Size(max = 150, message = "La institución no puede pasar de 150 caracteres.")
    private String institucion;
}
