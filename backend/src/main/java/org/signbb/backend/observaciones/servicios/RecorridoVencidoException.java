package org.signbb.backend.observaciones.servicios;

/**
 * El recorrido ya no se puede seguir (CU10): su sesión venció tras 8 horas sin
 * actividad, o el padre lo reinició y quedó reemplazado por uno nuevo. Se
 * responde 410 para que el cliente lleve a la persona al inicio con un aviso
 * claro, en vez de un 404 que parece un error.
 */
public class RecorridoVencidoException extends RuntimeException {

    /** Mensaje cuando la sesión venció por inactividad. */
    public static final String MENSAJE = "Esta consulta venció tras 8 horas sin actividad. Inicia una nueva.";

    /** Mensaje cuando el recorrido se reinició y ya hay uno nuevo. */
    public static final String MENSAJE_REINICIADO = "Esta consulta se reinició. Sigue con la nueva desde el inicio.";

    /** Recorrido vencido por inactividad. */
    public RecorridoVencidoException() {
        this(MENSAJE);
    }

    /** Recorrido que ya no se puede seguir, con el mensaje que ve la persona. */
    public RecorridoVencidoException(String mensaje) {
        super(mensaje);
    }
}
