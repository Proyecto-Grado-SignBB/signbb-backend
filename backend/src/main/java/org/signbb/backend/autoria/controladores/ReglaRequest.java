package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private String nombre;

    @Schema(description = "Explicación para el médico")
    private String descripcion;

    @Schema(description = "FND: lista de casos (OR), cada caso una lista de condiciones (AND)")
    private List<List<Condicion>> casos;

    @Schema(description = "Nivel de alerta al dispararse: CONSULTAR_MEDICO o URGENCIA")
    private NivelAlerta nivel;

    @Schema(description = "Id del nodo destino")
    private String nodoDestino;
}
