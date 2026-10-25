package org.signbb.backend.seguridad.servicios;

import java.time.Duration;

/**
 * Demasiados intentos con la misma cuenta: el ingreso o el registro queda
 * bloqueado un tiempo. La API la responde como 429 con el mensaje y la
 * cabecera {@code Retry-After}.
 */
public class IntentosExcedidosException extends RuntimeException {

    private final Duration espera;

    /** Crea la excepción con el tiempo que falta para poder reintentar. */
    public IntentosExcedidosException(Duration espera) {
        super(mensaje(espera));
        this.espera = espera;
    }

    /** Cuánto falta para que el bloqueo termine. */
    public Duration getEspera() {
        return espera;
    }

    /** El mensaje para el usuario, con los minutos redondeados hacia arriba. */
    static String mensaje(Duration espera) {
        long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
        return "Demasiados intentos fallidos. Vuelve a intentar en " + minutos
                + (minutos == 1 ? " minuto." : " minutos.");
    }
}
