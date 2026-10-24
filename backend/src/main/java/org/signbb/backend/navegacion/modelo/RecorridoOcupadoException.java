package org.signbb.backend.navegacion.modelo;

/**
 * Otra operación está cambiando ahora mismo el mismo recorrido. Se responde
 * 409 para que el cliente espere un momento y vuelva a consultar el estado.
 */
public class RecorridoOcupadoException extends RuntimeException {

    /** Mensaje que ve la persona. */
    public static final String MENSAJE = "Estamos guardando tu respuesta. Espera un momento.";

    public RecorridoOcupadoException() {
        super(MENSAJE);
    }
}
