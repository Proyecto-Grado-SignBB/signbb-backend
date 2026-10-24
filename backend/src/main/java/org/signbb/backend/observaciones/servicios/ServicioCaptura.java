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
 * Cada cambio del recorrido (responder o retroceder) se hace con un candado
 * por recorrido y es "todo o nada": el avance en Redis, la observación y la
 * fila del recorrido en Postgres quedan juntos o no queda ninguno.
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
    default RecorridoDTO registrarRespuesta(String recorridoId, TipoInteraccion tipo,
            List<String> opcionesSeleccionadas, Long usuarioId) {
        return registrarRespuesta(recorridoId, tipo, opcionesSeleccionadas, usuarioId, null);
    }

    /**
     * Igual que la anterior, pero si {@code idPreguntaEsperada} no es nulo y ya
     * no es la pregunta actual del recorrido, falla con
     * {@link PreguntaDesactualizadaException} sin registrar nada.
     */
    RecorridoDTO registrarRespuesta(String recorridoId, TipoInteraccion tipo, List<String> opcionesSeleccionadas,
            Long usuarioId, String idPreguntaEsperada);

    /**
     * Vuelve a la pregunta anterior y deja el recorrido guardado en Postgres.
     * Usa el mismo candado y la misma regla de "todo o nada" que
     * {@link #registrarRespuesta}.
     */
    RecorridoDTO retroceder(String recorridoId);
}