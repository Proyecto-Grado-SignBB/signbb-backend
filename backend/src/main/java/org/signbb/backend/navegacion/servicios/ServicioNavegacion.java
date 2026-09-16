package org.signbb.backend.navegacion.servicios;

import org.signbb.backend.crobservaciones.controladores.RecorridoDTO;

/**
 * API del módulo de navegación por el grafo clínico.
 *
 * <p>Expone el recorrido guiado como operaciones sin estado por petición:
 * {@link #iniciar} abre un recorrido y devuelve la primera pregunta;
 * {@link #responder} registra una respuesta y avanza un paso; {@link #consultar}
 * devuelve el estado actual.
 */
public interface ServicioNavegacion {

    /** Inicia un recorrido sobre el grafo clínico cargado y devuelve la primera pregunta. */
    RecorridoDTO iniciar();

    /** Registra la respuesta del cuidador y avanza el recorrido un paso. */
    RecorridoDTO responder(String recorridoId, String respuesta);

    /** Consulta el estado actual de un recorrido. */
    RecorridoDTO consultar(String recorridoId);
}
