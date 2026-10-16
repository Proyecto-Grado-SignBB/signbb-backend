package org.signbb.backend.seguridad.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ingreso con correo y contraseña.
 *
 * @param correo     correo de la cuenta
 * @param contrasena contraseña de la cuenta
 */
@Schema(description = "Ingreso con correo y contraseña")
public record LoginRequest(String correo, String contrasena) {
}
