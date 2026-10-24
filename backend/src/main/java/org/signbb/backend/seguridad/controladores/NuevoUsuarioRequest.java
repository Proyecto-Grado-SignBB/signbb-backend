package org.signbb.backend.seguridad.controladores;

import org.signbb.backend.seguridad.modelo.Rol;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.signbb.backend.config.PatronesTexto;

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
public record NuevoUsuarioRequest(
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.") @Pattern(regexp = PatronesTexto.UNA_LINEA, message = "El nombre no puede tener saltos de línea.") String nombre,
        @Size(max = 254, message = "El correo no puede pasar de 254 caracteres.") String correo,
        @Size(max = 72, message = "La contraseña no puede pasar de 72 caracteres.") String contrasena,
        Rol rol,
        @Size(max = 150, message = "La institución no puede pasar de 150 caracteres.") @Pattern(regexp = PatronesTexto.UNA_LINEA, message = "La institución no puede tener saltos de línea.") String institucion) {
}
