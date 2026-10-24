package org.signbb.backend.seguridad.servicios;

import java.time.Instant;
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
 *
 * <p>
 * MODIFICADO (2.4): se agrega {@link #guardarTelefonoPediatra}.
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
     * Abre una sesión con correo y contraseña. Si es un padre y el dispositivo
     * es seguro, el token dura más tiempo.
     *
     * @throws CredencialesInvalidasException con el mismo mensaje si el correo
     *                                        no existe, la contraseña no
     *                                        coincide o la cuenta está
     *                                        inactiva
     */
    SesionDTO ingresar(LoginRequest request);

    /** La cuenta activa con ese id, como usuario de la petición en curso. */
    Optional<UsuarioActual> usuarioActivo(Long id);

    /**
     * La cuenta activa con ese id, solo si el token emitido en {@code emitidoEn}
     * sigue valiendo: vacío si la cuenta no existe, está inactiva o cerró sus
     * sesiones después de que se emitió el token.
     */
    Optional<UsuarioActual> usuarioActivo(Long id, Instant emitidoEn);

    /**
     * Cierra la sesión en todos los dispositivos: los tokens emitidos hasta
     * ahora dejan de servir.
     *
     * @throws CredencialesInvalidasException si la cuenta ya no existe
     */
    void cerrarTodasLasSesiones(UsuarioActual usuario);

    /** La cuenta del usuario actual. */
    UsuarioDTO yo(UsuarioActual usuario);

    /**
     * Guarda el teléfono del pediatra del padre; vacío o {@code null} lo borra.
     *
     * @throws CuentaInvalidaException si el teléfono no tiene un formato válido
     */
    UsuarioDTO guardarTelefonoPediatra(UsuarioActual padre, String telefono);

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
