package org.signbb.backend.observaciones.servicios;

import java.util.List;

import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.controladores.ResumenRecorridoDTO;
import org.signbb.backend.seguridad.modelo.UsuarioActual;

/**
 * Dueño de cada recorrido: lo registra al iniciarlo, revisa que quien lo
 * consulta o responde sea el padre que lo inició y lleva su estado para "Mis
 * recorridos". La navegación ({@code ServicioNavegacion}) no cambia.
 *
 * <p>
 * MODIFICADO (CU10/CU11): se agregan {@link #exigirVigente(String)}, que
 * detecta una consulta vencida (410) y la marca {@code VENCIDO}, y
 * {@link #reiniciar(String, UsuarioActual)}; "Mis recorridos" marca como
 * vencidas las consultas en curso cuya sesión ya no existe.
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

    /**
     * Recorridos del padre, del más nuevo al más viejo. Los que siguen
     * {@code EN_CURSO} pero ya no tienen sesión quedan {@code VENCIDO}.
     */
    List<ResumenRecorridoDTO> deUsuario(UsuarioActual padre);

    /**
     * Exige que el recorrido se pueda seguir. Si estaba en curso y su sesión
     * venció, lo marca {@code VENCIDO} antes de fallar.
     *
     * @throws RecorridoVencidoException si venció o se reinició (410)
     */
    void exigirVigente(String recorridoId);

    /**
     * Reinicia la consulta (CU11): el recorrido queda {@code ABANDONADO}, se
     * borra su sesión y empieza uno nuevo a nombre del mismo padre.
     *
     * @return la primera pregunta del recorrido nuevo
     * @throws RecorridoFinalizadoException si el recorrido ya terminó (409)
     * @throws RecorridoVencidoException si ya se había reiniciado (410)
     * @throws org.signbb.backend.navegacion.modelo.RecorridoOcupadoException si
     *         otra operación lo está cambiando (409)
     */
    RecorridoDTO reiniciar(String recorridoId, UsuarioActual padre);
}
