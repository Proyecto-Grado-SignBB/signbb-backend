package org.signbb.backend.reglas.modelo;

/**
 * Nivel de alerta ordenado por severidad según el orden de declaración:
 * {@code NORMAL < CONSULTAR_MEDICO < URGENCIA}.
 */
public enum NivelAlerta {

    /** Sin señales de alarma; implícito cuando ninguna regla se activa. */
    NORMAL,

    /** Requiere valoración médica pronta. */
    CONSULTAR_MEDICO,

    /** Derivación inmediata al servicio de urgencias. */
    URGENCIA
}
