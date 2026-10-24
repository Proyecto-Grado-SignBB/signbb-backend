package org.signbb.backend.navegacion.modelo;

import java.util.function.Supplier;

/**
 * Candado por recorrido: deja pasar de a una las operaciones que cambian un
 * mismo recorrido (responder y retroceder). Así un doble clic, o dos pestañas
 * abiertas, no pueden avanzar el recorrido dos veces a la vez.
 *
 * <p>
 * La implementación vive en {@code cache} ({@code CandadoRecorridoRedis}), igual
 * que {@link RepositorioSesion}.
 */
public interface CandadoRecorrido {

    /**
     * Ejecuta la acción con el candado del recorrido tomado y lo suelta al
     * terminar, aunque la acción falle.
     *
     * @throws RecorridoOcupadoException si otra operación ya tiene el candado
     */
    <T> T conCandado(String recorridoId, Supplier<T> accion);
}
