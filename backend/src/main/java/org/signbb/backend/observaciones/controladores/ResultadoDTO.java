package org.signbb.backend.observaciones.controladores;

import java.util.List;

/**
 * Desenlace de un recorrido FINALIZADO.
 *
 * <p>
 * {@code nivel} es el {@code NivelAlerta} resultante (NORMAL si se llegó a un
 * terminal del grafo sin disparar reglas); {@code destinoId} y {@code mensaje}
 * son el id y el contenido del nodo terminal; {@code razones} son los ids de
 * las reglas de nivel máximo que dispararon (vacío si NORMAL);
 * {@code caminoCausal} es la secuencia de nodos y condiciones hasta el destino;
 * y {@code recomendaciones} son todas las recomendaciones alcanzadas, cada una
 * con su propio camino. Solo viene con datos cuando el recorrido cierra sin
 * alerta (ramas de selección múltiple que terminaron en recomendación, con o
 * sin el cierre por el máximo de preguntas); con una alerta, o si no hay
 * ninguna, es una lista vacía.
 */
public record ResultadoDTO(
                String nivel,
                String destinoId,
                String mensaje,
                List<String> razones,
                List<PasoCausalDTO> caminoCausal,
                List<RecomendacionDTO> recomendaciones) {
}
