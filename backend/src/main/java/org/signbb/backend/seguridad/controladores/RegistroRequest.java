package org.signbb.backend.seguridad.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Registro de un padre o acudiente en la app de consulta.
 *
 * @param nombre     cómo quiere que lo llamen
 * @param correo     correo con el que va a ingresar
 * @param contrasena al menos 8 caracteres
 */
@Schema(description = "Registro de un padre o acudiente")
public record RegistroRequest(String nombre, String correo, String contrasena) {
}
