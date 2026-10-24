package org.signbb.backend.autoria.controladores;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.signbb.backend.config.PatronesTexto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de una base de reglas, para crearla o cambiar su nombre y descripción.
 * {@code grafoId} es el grafo publicado al que pertenecen las reglas
 * ({@code grafo_raiz} o un subgrafo) y se fija al crear.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaseReglaRequest {
    @Schema(description = "Nombre de la base de reglas, para el tablero")
    @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
    @Pattern(regexp = PatronesTexto.UNA_LINEA, message = "El nombre no puede tener saltos de línea.")
    private String nombre;

    @Schema(description = "Descripción de la base de reglas, para el tablero")
    @Size(max = 500, message = "La descripción no puede pasar de 500 caracteres.")
    @Pattern(regexp = PatronesTexto.TEXTO_LARGO, message = "La descripción tiene caracteres que no se aceptan.")
    private String descripcion;

    @Schema(description = "Grafo publicado al que pertenecen las reglas (grafo_raiz o un subgrafo)")
    @Size(max = 100, message = "El grafo no es válido (id demasiado largo).")
    private String grafoId;

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
