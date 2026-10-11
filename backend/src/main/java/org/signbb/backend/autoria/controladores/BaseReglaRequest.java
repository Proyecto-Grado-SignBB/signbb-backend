package org.signbb.backend.autoria.controladores;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de una base de reglas, para crearla o cambiar su nombre. {@code grafoId}
 * es el grafo publicado al que pertenecen las reglas ({@code grafo_raiz} o un
 * subgrafo) y se fija al crear.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaseReglaRequest {
    @Schema(description = "Nombre de la base de reglas, para el tablero")
    private String nombre;

    @Schema(description = "Grafo publicado al que pertenecen las reglas (grafo_raiz o un subgrafo)")
    private String grafoId;

    @Schema(description = "Autor del contenido")
    private String autor;

    @Schema(description = "Institución del autor")
    private String institucion;
}
