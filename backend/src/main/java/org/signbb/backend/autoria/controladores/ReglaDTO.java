package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

import io.swagger.v3.oas.annotations.media.Schema;

/** Vista REST de una regla del borrador. */
@Schema(description = "Regla de una base de reglas.")
public record ReglaDTO(
        @Schema(description = "Id de la regla") String id,
        @Schema(description = "Nombre de la regla") String nombre,
        @Schema(description = "Explicación para el médico") String descripcion,
        @Schema(description = "FND: lista de casos (OR), cada caso una lista de condiciones (AND)")
        List<List<Condicion>> casos,
        @Schema(description = "Nivel de alerta") NivelAlerta nivel,
        @Schema(description = "Id del nodo destino") String nodoDestino) {

    /** DTO de una regla a partir de su borrador. */
    public static ReglaDTO desde(String id, BorradorRegla regla) {
        return new ReglaDTO(id, regla.nombre(), regla.descripcion(), aListas(regla.casos()), regla.nivel(),
                regla.nodoDestino());
    }

    /** Aplana cada caso a su lista de condiciones. */
    private static List<List<Condicion>> aListas(List<Caso> casos) {
        return casos.stream().map(Caso::condiciones).toList();
    }
}
