package org.signbb.backend.seguridad.modelo;

/**
 * Usuario de la petición en curso, armado desde el JWT y la cuenta guardada.
 * Los controladores lo reciben como parámetro y se lo pasan a los servicios.
 *
 * @param id          id de la cuenta
 * @param nombre      nombre para mostrar (es el autor de lo que crea)
 * @param correo      correo con el que ingresa
 * @param rol         rol de la cuenta
 * @param institucion institución del médico, o {@code null} para un padre
 */
public record UsuarioActual(Long id, String nombre, String correo, Rol rol, String institucion) {

    /** True si es médico supervisor. */
    public boolean esSupervisor() {
        return rol == Rol.MEDICO_SUPERVISOR;
    }
}
