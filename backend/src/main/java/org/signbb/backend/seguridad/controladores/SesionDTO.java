package org.signbb.backend.seguridad.controladores;

import java.time.Instant;

/**
 * Sesión abierta: el token para {@code Authorization: Bearer} y el usuario.
 *
 * @param token    JWT firmado
 * @param expiraEn cuándo deja de valer; después hay que volver a ingresar
 * @param usuario  la cuenta que ingresó
 */
public record SesionDTO(String token, Instant expiraEn, UsuarioDTO usuario) {
}
