package org.signbb.backend.seguridad.servicios;

/**
 * No se pudo identificar al usuario: correo o contraseña incorrectos, cuenta
 * inactiva o sesión que ya no vale. La API la responde como 401 con un
 * mensaje que no revela cuál de esas fue.
 */
public class CredencialesInvalidasException extends RuntimeException {

    /** Crea la excepción con un mensaje para el usuario. */
    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
