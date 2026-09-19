package org.signbb.backend.observaciones.controladores;

import java.util.List;

/**
 * Desenlace de un recorrido FINALIZADO.
 *
 * <p>
 * {@code nivel} es el {@code NivelAlerta} resultante (NORMAL si se llegó a un
 * terminal del grafo sin disparar reglas); {@code destinoId} y {@code mensaje}
 * son el id y el contenido del nodo terminal; {@code razones} son los ids de
 * las reglas de nivel máximo que dispararon (vacío si NORMAL); y
 * {@code caminoCausal} es la secuencia de nodos y condiciones hasta el destino.
 */
public record ResultadoDTO(
                String nivel,
                String destinoId,
                String mensaje,
                List<String> razones,
                List<PasoCausalDTO> caminoCausal) {
}
