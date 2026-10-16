package org.signbb.backend.seguridad.servicios;

import java.util.List;
import java.util.Optional;

import org.signbb.backend.seguridad.controladores.LoginRequest;
import org.signbb.backend.seguridad.controladores.NuevoUsuarioRequest;
import org.signbb.backend.seguridad.controladores.RegistroRequest;
import org.signbb.backend.seguridad.controladores.SesionDTO;
import org.signbb.backend.seguridad.controladores.UsuarioDTO;
import org.signbb.backend.seguridad.modelo.UsuarioActual;

/**
 * Cuentas de usuario: registro de padres, ingreso y administración de las
 * cuentas clínicas por un supervisor.
 */
public interface ServicioCuentas {

    /**
     * Registra un padre y le abre la sesión.
     *
     * @throws CuentaInvalidaException si falta un dato, el correo ya existe o
     *                                 la contraseña es corta
     */
    SesionDTO registrarPadre(RegistroRequest request);

    /**
     * Abre una sesión con correo y contraseña.
     *
     * @throws CredencialesInvalidasException con el mismo mensaje si el correo
     *                                        no existe, la contraseña no
     *                                        coincide o la cuenta está
     *                                        inactiva
     */
    SesionDTO ingresar(LoginRequest request);

    /** La cuenta activa con ese id, como usuario de la petición en curso. */
    Optional<UsuarioActual> usuarioActivo(Long id);

    /** La cuenta del usuario actual. */
    UsuarioDTO yo(UsuarioActual usuario);

    /**
     * Crea una cuenta de médico o de supervisor.
     *
     * @throws CuentaInvalidaException si el rol es PADRE o los datos no sirven
     */
    UsuarioDTO crearClinico(NuevoUsuarioRequest request);

    /** Las cuentas clínicas (médicos y supervisores), por nombre. */
    List<UsuarioDTO> listarClinicos();

    /**
     * Activa o desactiva una cuenta.
     *
     * @throws CuentaInvalidaException si el supervisor intenta desactivarse
     *                                 a sí mismo
     */
    UsuarioDTO cambiarActivo(Long id, boolean activo, UsuarioActual usuario);
}
