package org.signbb.backend.seguridad.modelo;

/**
 * Tipo de límite de intentos persistido en {@code limite_intentos}.
 *
 * <p>
 * El alcance actual limita únicamente por correo/cuenta: no hay límites por
 * dirección IP ni globales. Cada tipo tiene su propio máximo y su propio
 * tiempo de bloqueo en {@code signbb.seguridad.*}.
 */
public enum TipoLimite {

    /** Intentos fallidos de ingreso para un correo normalizado. */
    LOGIN_CUENTA,

    /** Intentos de registro para un correo normalizado. */
    REGISTRO_CUENTA
}
