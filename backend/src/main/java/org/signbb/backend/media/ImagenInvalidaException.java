package org.signbb.backend.media;

/** La imagen que se quiso subir no se puede aceptar; el mensaje es para el médico. */
public class ImagenInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Crea la excepción con el mensaje para el médico. */
    public ImagenInvalidaException(String mensaje) {
        super(mensaje);
    }
}
