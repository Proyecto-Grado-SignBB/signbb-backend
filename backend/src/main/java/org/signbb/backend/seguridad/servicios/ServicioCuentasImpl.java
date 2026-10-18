package org.signbb.backend.seguridad.servicios;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.seguridad.controladores.LoginRequest;
import org.signbb.backend.seguridad.controladores.NuevoUsuarioRequest;
import org.signbb.backend.seguridad.controladores.RegistroRequest;
import org.signbb.backend.seguridad.controladores.SesionDTO;
import org.signbb.backend.seguridad.controladores.UsuarioDTO;
import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ServicioCuentas} sobre {@link RepositorioUsuario},
 * con contraseñas en BCrypt y tokens de {@link ServicioTokens}.
 */
@Service
@Transactional
public class ServicioCuentasImpl implements ServicioCuentas {

    /** Largo mínimo de una contraseña. */
    static final int MINIMO_CONTRASENA = 8;

    /** Mismo mensaje para todo fallo de ingreso: no revela qué falló. */
    static final String MENSAJE_INGRESO = "Correo o contraseña incorrectos.";

    /** Mensaje del 401 cuando la sesión ya no sirve. */
    static final String MENSAJE_SESION_TERMINADA = "Tu sesión terminó. Vuelve a ingresar.";

    private final RepositorioUsuario usuarios;
    private final PasswordEncoder codificador;
    private final ServicioTokens tokens;

    /** Inyecta el repositorio, el codificador de contraseñas y el emisor de tokens. */
    public ServicioCuentasImpl(RepositorioUsuario usuarios, PasswordEncoder codificador,
            ServicioTokens tokens) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.tokens = tokens;
    }

    @Override
    public SesionDTO registrarPadre(RegistroRequest request) {
        UsuarioEntity u = nueva(request.nombre(), request.correo(), request.contrasena(), Rol.PADRE, null);
        return sesion(usuarios.save(u), request.esDispositivoSeguro());
    }

    @Override
    @Transactional(readOnly = true)
    public SesionDTO ingresar(LoginRequest request) {
        String correo = normalizarCorreo(request.correo());
        String contrasena = request.contrasena() == null ? "" : request.contrasena();
        UsuarioEntity u = correo == null ? null : usuarios.findByCorreo(correo).orElse(null);
        if (u == null || !u.isActivo() || !codificador.matches(contrasena, u.getHashContrasena())) {
            throw new CredencialesInvalidasException(MENSAJE_INGRESO);
        }
        return sesion(u, request.esDispositivoSeguro());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsuarioActual> usuarioActivo(Long id) {
        return usuarios.findById(id).filter(UsuarioEntity::isActivo).map(UsuarioEntity::comoActual);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsuarioActual> usuarioActivo(Long id, Instant emitidoEn) {
        return usuarios.findById(id).filter(UsuarioEntity::isActivo)
                .filter(u -> tokenVigente(u, emitidoEn)).map(UsuarioEntity::comoActual);
    }

    @Override
    public void cerrarTodasLasSesiones(UsuarioActual usuario) {
        UsuarioEntity u = usuarios.findById(usuario.id())
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_SESION_TERMINADA));
        // El «iat» del token va en segundos y se redondea hacia abajo, así que un
        // token emitido en el mismo segundo del cierre también queda invalidado.
        u.setSesionValidaDesde(Instant.now());
        usuarios.save(u);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDTO yo(UsuarioActual usuario) {
        return usuarios.findById(usuario.id()).map(UsuarioDTO::desde)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_SESION_TERMINADA));
    }

    @Override
    public UsuarioDTO crearClinico(NuevoUsuarioRequest request) {
        if (request.rol() == null || !request.rol().esClinico()) {
            throw new CuentaInvalidaException(
                    "El rol debe ser MEDICO o MEDICO_SUPERVISOR. Los padres se registran solos.");
        }
        UsuarioEntity u = nueva(request.nombre(), request.correo(), request.contrasena(), request.rol(),
                vacioANull(request.institucion()));
        return UsuarioDTO.desde(usuarios.save(u));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarClinicos() {
        return usuarios.findByRolInOrderByNombreAsc(List.of(Rol.MEDICO, Rol.MEDICO_SUPERVISOR)).stream()
                .map(UsuarioDTO::desde).toList();
    }

    @Override
    public UsuarioDTO cambiarActivo(Long id, boolean activo, UsuarioActual usuario) {
        if (!activo && id.equals(usuario.id())) {
            throw new CuentaInvalidaException("No puedes desactivar tu propia cuenta.");
        }
        UsuarioEntity u = usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));
        u.setActivo(activo);
        return UsuarioDTO.desde(usuarios.save(u));
    }

    /** Valida los datos y arma una cuenta nueva (sin guardarla). */
    private UsuarioEntity nueva(String nombre, String correo, String contrasena, Rol rol, String institucion) {
        if (nombre == null || nombre.isBlank()) {
            throw new CuentaInvalidaException("Escribe el nombre.");
        }
        String normalizado = normalizarCorreo(correo);
        if (normalizado == null || !normalizado.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new CuentaInvalidaException("Escribe un correo válido.");
        }
        if (contrasena == null || contrasena.length() < MINIMO_CONTRASENA) {
            throw new CuentaInvalidaException(
                    "La contraseña debe tener al menos " + MINIMO_CONTRASENA + " caracteres.");
        }
        if (usuarios.existsByCorreo(normalizado)) {
            throw new CuentaInvalidaException("Ya existe una cuenta con ese correo.");
        }
        UsuarioEntity u = new UsuarioEntity();
        u.setNombre(nombre.trim());
        u.setCorreo(normalizado);
        u.setHashContrasena(codificador.encode(contrasena));
        u.setRol(rol);
        u.setInstitucion(institucion);
        u.setActivo(true);
        u.setCreadoEn(Instant.now());
        return u;
    }

    /** Un token vale si se emitió desde el último «cerrar todas las sesiones». */
    private static boolean tokenVigente(UsuarioEntity u, Instant emitidoEn) {
        Instant desde = u.getSesionValidaDesde();
        return desde == null || (emitidoEn != null && !emitidoEn.isBefore(desde));
    }

    /** Abre la sesión de la cuenta; el token dura según su rol y el dispositivo. */
    private SesionDTO sesion(UsuarioEntity u, boolean dispositivoSeguro) {
        TokenEmitido t = tokens.emitir(u, dispositivoSeguro);
        return new SesionDTO(t.token(), t.expiraEn(), UsuarioDTO.desde(u));
    }

    /** El correo sin espacios y en minúsculas, o {@code null} si viene vacío. */
    static String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            return null;
        }
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
