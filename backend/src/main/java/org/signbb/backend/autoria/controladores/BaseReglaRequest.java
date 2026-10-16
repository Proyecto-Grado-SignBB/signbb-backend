package org.signbb.backend.autoria.controladores;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private String nombre;

    @Schema(description = "Descripción de la base de reglas, para el tablero")
    private String descripcion;

    @Schema(description = "Grafo publicado al que pertenecen las reglas (grafo_raiz o un subgrafo)")
    private String grafoId;

    /** Obsoleto: se ignora; el autor sale de la cuenta que hace la petición. */
    @Schema(description = "Obsoleto: se ignora. El autor sale de la cuenta que hace la petición",
            deprecated = true)
    private String autor;

    /** Obsoleto: se ignora; la institución sale de la cuenta que hace la petición. */
    @Schema(description = "Obsoleto: se ignora. La institución sale de la cuenta que hace la petición",
            deprecated = true)
    private String institucion;
}
