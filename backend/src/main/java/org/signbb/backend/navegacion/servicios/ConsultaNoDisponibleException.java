package org.signbb.backend.navegacion.servicios;

/**
 * No hay ningún subgrafo publicado, así que el contenido clínico no tiene
 * opciones desde la pregunta inicial. La pantalla de inicio deshabilita la
 * consulta y cualquier intento de abrir un recorrido nuevo responde 503.
 */
public class ConsultaNoDisponibleException extends RuntimeException {

    /** Mensaje que ve el cuidador. */
    public static final String MENSAJE = "La consulta no está disponible en estos momentos.";

    /** Crea la excepción con el mensaje para el cuidador. */
    public ConsultaNoDisponibleException() {
        super(MENSAJE);
    }
}
