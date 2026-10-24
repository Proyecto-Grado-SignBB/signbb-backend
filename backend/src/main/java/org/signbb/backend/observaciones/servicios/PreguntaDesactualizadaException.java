package org.signbb.backend.observaciones.servicios;

/**
 * La respuesta es para una pregunta que ya no es la actual del recorrido (por
 * ejemplo, un segundo clic que llega cuando la primera respuesta ya avanzó). Se
 * responde 409 y el cliente vuelve a consultar el recorrido; la respuesta no se
 * aplica a la pregunta siguiente.
 */
public class PreguntaDesactualizadaException extends RuntimeException {

    /** Mensaje que ve la persona. */
    public static final String MENSAJE = "Esa pregunta ya fue respondida. Te mostramos la que sigue.";

    public PreguntaDesactualizadaException() {
        super(MENSAJE);
    }
}
