package org.signbb.backend.observaciones.servicios;

import java.util.List;

import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.modelo.TipoInteraccion;

/**
 * API del módulo de captura: recibe la(s) opción(es) que el cuidador marcó
 * para la pregunta actual, las valida y audita, y las entrega íntegras a
 * {@link org.signbb.backend.navegacion.servicios.ServicioNavegacion}.
 *
 * <p>
 * No decide cuál opción domina en una selección múltiple — eso lo resuelve
 * el Módulo de Navegación (ver
 * {@code ServicioNavegacion.responder(String, List)}).
 */
public interface ServicioCaptura {

    /**
     * Registra lo que el cuidador marcó para la pregunta actual y avanza el
     * recorrido. {@code opcionesSeleccionadas} trae un solo elemento para
     * SI_NO/SELECCION_UNICA, uno o más para SELECCION_MULTIPLE.
     * {@code usuarioId} es el padre que responde y queda en la auditoría.
     */
    RecorridoDTO registrarRespuesta(String recorridoId, TipoInteraccion tipo, List<String> opcionesSeleccionadas,
            Long usuarioId);
}