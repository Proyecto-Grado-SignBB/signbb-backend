package org.signbb.backend.autoria.servicios;

/**
 * Una edición del lienzo que no se puede aplicar (por ejemplo, una segunda
 * primera pregunta o un enlace que forma un círculo). El mensaje está escrito
 * para el médico; la API lo responde como 422.
 */
public class EdicionInvalidaException extends RuntimeException {

    /** Crea la excepción con un mensaje para el médico. */
    public EdicionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
