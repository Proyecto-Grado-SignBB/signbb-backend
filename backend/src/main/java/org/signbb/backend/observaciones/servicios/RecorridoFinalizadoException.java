package org.signbb.backend.observaciones.servicios;

/**
 * Se pidió reiniciar (CU09) un recorrido que ya terminó. Se responde 409: el
 * resultado queda tal cual en "Mis recorridos" y, si hace falta, el padre
 * inicia una consulta nueva.
 */
public class RecorridoFinalizadoException extends RuntimeException {

    /** Mensaje que ve la persona. */
    public static final String MENSAJE = "Esta consulta ya terminó. Inicia una nueva si la necesitas.";

    public RecorridoFinalizadoException() {
        super(MENSAJE);
    }
}
