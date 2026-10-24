package org.signbb.backend.observaciones.controladores;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Desenlace de un recorrido FINALIZADO.
 *
 * <p>
 * {@code nivel} es el {@code NivelAlerta} resultante (NORMAL si se llegó a un
 * terminal del grafo sin disparar reglas); {@code destinoId} y {@code mensaje}
 * son el id y el contenido del nodo terminal; {@code razones} son los nombres
 * legibles de las reglas de nivel máximo que dispararon (vacío si NORMAL);
 * {@code caminoCausal} es la secuencia de nodos y condiciones hasta el destino;
 * y {@code recomendaciones} son todas las recomendaciones alcanzadas, cada una
 * con su propio camino. Solo viene con datos cuando el recorrido cierra sin
 * alerta (ramas de selección múltiple que terminaron en recomendación, con o
 * sin el cierre por el máximo de preguntas); con una alerta, o si no hay
 * ninguna, es una lista vacía.
 *
 * <p>
 * MODIFICADO (2.6): se corrige la descripción de {@code razones}, que ya eran
 * los nombres de las reglas y no sus ids.
 */
@Schema(description = "Desenlace de un recorrido finalizado.")
public record ResultadoDTO(
                @Schema(description = "Nivel de alerta resultante; NORMAL si no disparó reglas")
                String nivel,
                @Schema(description = "Id del nodo terminal") String destinoId,
                @Schema(description = "Contenido del nodo terminal") String mensaje,
                @Schema(description = "Nombres de las reglas de nivel máximo que dispararon; vacío si NORMAL")
                List<String> razones,
                @Schema(description = "Secuencia de nodos y condiciones hasta el destino")
                List<PasoCausalDTO> caminoCausal,
                @Schema(description = "Recomendaciones alcanzadas; vacía si hubo alerta")
                List<RecomendacionDTO> recomendaciones) {
}
