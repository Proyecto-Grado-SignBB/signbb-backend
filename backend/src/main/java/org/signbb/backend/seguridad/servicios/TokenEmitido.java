package org.signbb.backend.seguridad.servicios;

import java.time.Instant;

/**
 * Un JWT recién emitido.
 *
 * @param token    el JWT firmado
 * @param expiraEn cuándo deja de valer
 */
public record TokenEmitido(String token, Instant expiraEn) {
}
