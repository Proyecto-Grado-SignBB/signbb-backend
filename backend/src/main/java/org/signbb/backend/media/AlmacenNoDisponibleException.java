package org.signbb.backend.media;

/** El almacén de imágenes (Silo) no responde. Es transitorio: se puede reintentar. */
public class AlmacenNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Crea la excepción con su causa. */
    public AlmacenNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
