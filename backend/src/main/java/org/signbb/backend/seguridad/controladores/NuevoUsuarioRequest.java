package org.signbb.backend.seguridad.controladores;

import org.signbb.backend.seguridad.modelo.Rol;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuenta clínica nueva, creada por un supervisor.
 *
 * @param nombre      nombre del médico (será el autor de lo que cree)
 * @param correo      correo con el que va a ingresar
 * @param contrasena  contraseña inicial, al menos 8 caracteres
 * @param rol         MEDICO o MEDICO_SUPERVISOR
 * @param institucion institución del médico
 */
@Schema(description = "Cuenta clínica nueva (solo MEDICO o MEDICO_SUPERVISOR)")
public record NuevoUsuarioRequest(String nombre, String correo, String contrasena, Rol rol,
        String institucion) {
}
