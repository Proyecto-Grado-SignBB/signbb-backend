package org.signbb.backend.observaciones.controladores;

import java.time.Instant;

import org.signbb.backend.observaciones.datos.entidades.RecorridoEntity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Fila de "Mis recorridos".
 *
 * @param recorridoId  id del recorrido
 * @param estado       {@code EN_CURSO} o {@code FINALIZADO}
 * @param nivel        nivel de alerta del resultado, o {@code null} si sigue en curso
 * @param mensaje      mensaje de la conclusión, o {@code null} si sigue en curso
 * @param iniciadoEn   cuándo empezó
 * @param finalizadoEn cuándo terminó, o {@code null}
 */
@Schema(description = "Fila de \"Mis recorridos\".")
public record ResumenRecorridoDTO(
        @Schema(description = "Id del recorrido") String recorridoId,
        @Schema(description = "EN_CURSO o FINALIZADO") String estado,
        @Schema(description = "Nivel de alerta del resultado, o nulo si sigue en curso") String nivel,
        @Schema(description = "Mensaje de la conclusión, o nulo si sigue en curso") String mensaje,
        @Schema(description = "Cuándo empezó") Instant iniciadoEn,
        @Schema(description = "Cuándo terminó, o nulo") Instant finalizadoEn) {

    /** Resumen de un recorrido guardado. */
    public static ResumenRecorridoDTO desde(RecorridoEntity r) {
        return new ResumenRecorridoDTO(r.getId(), r.getEstado(), r.getNivel(), r.getMensaje(),
                r.getIniciadoEn(), r.getFinalizadoEn());
    }
}
