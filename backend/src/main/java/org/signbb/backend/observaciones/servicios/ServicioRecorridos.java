package org.signbb.backend.observaciones.servicios;

import java.util.List;

import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.controladores.ResumenRecorridoDTO;
import org.signbb.backend.seguridad.modelo.UsuarioActual;

/**
 * Dueño de cada recorrido: lo registra al iniciarlo, revisa que quien lo
 * consulta o responde sea el padre que lo inició y lleva su estado para "Mis
 * recorridos". La navegación ({@code ServicioNavegacion}) no cambia.
 */
public interface ServicioRecorridos {

    /** Inicia un recorrido a nombre del padre y lo registra. */
    RecorridoDTO iniciar(UsuarioActual padre);

    /**
     * Exige que el recorrido sea del padre.
     *
     * @throws org.signbb.backend.config.RecursoNoEncontradoException si no existe o
     *         es de otro padre (404, para no revelar que existe)
     */
    void exigirDueno(String recorridoId, UsuarioActual padre);

    /** Guarda el estado y el resultado que devolvió la navegación. */
    RecorridoDTO actualizar(RecorridoDTO recorrido);

    /** Recorridos del padre, del más nuevo al más viejo. */
    List<ResumenRecorridoDTO> deUsuario(UsuarioActual padre);
}
