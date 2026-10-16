package org.signbb.backend.seguridad.controladores;

import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.modelo.Rol;

/**
 * Una cuenta, sin la contraseña.
 *
 * @param id          id de la cuenta
 * @param nombre      nombre para mostrar
 * @param correo      correo con el que ingresa
 * @param rol         rol de la cuenta
 * @param institucion institución del médico, o {@code null}
 * @param activo      si puede ingresar
 */
public record UsuarioDTO(Long id, String nombre, String correo, Rol rol, String institucion,
        boolean activo) {

    /** Arma el DTO desde la entidad. */
    public static UsuarioDTO desde(UsuarioEntity u) {
        return new UsuarioDTO(u.getId(), u.getNombre(), u.getCorreo(), u.getRol(), u.getInstitucion(),
                u.isActivo());
    }
}
