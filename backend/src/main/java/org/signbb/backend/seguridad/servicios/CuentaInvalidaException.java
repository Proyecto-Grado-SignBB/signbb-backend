package org.signbb.backend.seguridad.servicios;

/**
 * Los datos de una cuenta nueva o de un cambio no se pueden aceptar (correo
 * repetido, contraseña corta, rol no permitido). La API la responde como 422.
 */
public class CuentaInvalidaException extends RuntimeException {

    /** Crea la excepción con un mensaje para el usuario. */
    public CuentaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
