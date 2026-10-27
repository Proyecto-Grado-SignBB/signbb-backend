package org.signbb.backend.observaciones.controladores;

import org.signbb.backend.navegacion.servicios.ConsultaNoDisponibleException;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Disponibilidad de la consulta para la pantalla de inicio del cuidador.
 *
 * @param disponible si hay al menos un subgrafo publicado que permite iniciar
 *                   un recorrido
 * @param mensaje    texto para el cuidador cuando no está disponible; nulo si sí
 */
@Schema(description = "Disponibilidad de la consulta para iniciar un recorrido")
public record DisponibilidadDTO(
        @Schema(description = "True si se puede iniciar una consulta") boolean disponible,
        @Schema(description = "Mensaje para el cuidador cuando no está disponible") String mensaje) {

    /** Consulta disponible. */
    public static DisponibilidadDTO habilitada() {
        return new DisponibilidadDTO(true, null);
    }

    /** Consulta no disponible porque no hay subgrafos publicados. */
    public static DisponibilidadDTO noDisponible() {
        return new DisponibilidadDTO(false, ConsultaNoDisponibleException.MENSAJE);
    }
}
