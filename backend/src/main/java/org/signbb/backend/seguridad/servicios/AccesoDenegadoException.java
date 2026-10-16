package org.signbb.backend.seguridad.servicios;

/**
 * El usuario ingresó pero no puede hacer esa acción (por ejemplo, editar el
 * subgrafo de otro médico o aprobar lo propio). La API la responde como 403.
 */
public class AccesoDenegadoException extends RuntimeException {

    /** Crea la excepción con un mensaje para el usuario. */
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
