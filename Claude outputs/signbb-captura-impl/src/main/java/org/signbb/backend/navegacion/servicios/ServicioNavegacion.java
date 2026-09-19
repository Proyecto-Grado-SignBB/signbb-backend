package org.signbb.backend.navegacion.servicios;

import java.util.List;

import org.signbb.backend.crobservaciones.controladores.RecorridoDTO;

/**
 * API del módulo de navegación por el grafo clínico.
 *
 * <p>Expone el recorrido guiado como operaciones sin estado por petición:
 * {@link #iniciar} abre un recorrido y devuelve la primera pregunta;
 * {@link #responder} registra una respuesta y avanza un paso; {@link #consultar}
 * devuelve el estado actual.
 *
 * <p>MODIFICADO: se agrega la sobrecarga {@link #responder(String, List)} para
 * selección múltiple. La firma de un solo {@code String} no cambia.
 */
public interface ServicioNavegacion {

    /** Inicia un recorrido sobre el grafo clínico cargado y devuelve la primera pregunta. */
    RecorridoDTO iniciar();

    /** Registra la respuesta del cuidador y avanza el recorrido un paso. */
    RecorridoDTO responder(String recorridoId, String respuesta);

    /**
     * Registra una o más respuestas marcadas a la vez para la misma pregunta
     * (selección múltiple) y avanza el recorrido un paso. Con una sola
     * respuesta, se comporta exactamente igual que {@link #responder(String, String)}.
     * Cuando hay más de una, se avanza hacia el nodo destino de mayor
     * {@code peso} entre los candidatos (ver {@code MotorNavegacion.paso}).
     */
    RecorridoDTO responder(String recorridoId, List<String> respuestas);

    /** Consulta el estado actual de un recorrido. */
    RecorridoDTO consultar(String recorridoId);
}
