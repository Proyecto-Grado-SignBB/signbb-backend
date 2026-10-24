package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.signbb.backend.config.PatronesTexto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Regla que manda el panel de reglas, para crearla o editarla. {@code casos} es
 * la FND: una lista de casos (OR) donde cada caso es una lista de condiciones
 * (AND) de pares nodo→respuesta. {@code nodoDestino} es el id de un nodo del
 * grafo (local si la base es de un subgrafo).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReglaRequest {
    @Schema(description = "Nombre de la regla")
    @Size(max = 100, message = "El nombre de la regla no puede pasar de 100 caracteres.")
    @Pattern(regexp = PatronesTexto.UNA_LINEA, message = "El nombre de la regla no puede tener saltos de línea.")
    private String nombre;

    @Schema(description = "Explicación para el médico")
    @Size(max = 500, message = "La descripción de la regla no puede pasar de 500 caracteres.")
    @Pattern(regexp = PatronesTexto.TEXTO_LARGO, message = "La descripción de la regla tiene caracteres que no se aceptan.")
    private String descripcion;

    @Schema(description = "FND: lista de casos (OR), cada caso una lista de condiciones (AND)")
    @Size(max = 20, message = "Una regla no puede tener más de 20 casos.")
    private List<@Size(max = 20, message = "Un caso no puede tener más de 20 condiciones.") List<Condicion>> casos;

    @Schema(description = "Nivel de alerta al dispararse: CONSULTAR_MEDICO o URGENCIA")
    private NivelAlerta nivel;

    @Schema(description = "Id del nodo destino")
    @Size(max = 100, message = "El destino no es válido (id demasiado largo).")
    private String nodoDestino;
}
