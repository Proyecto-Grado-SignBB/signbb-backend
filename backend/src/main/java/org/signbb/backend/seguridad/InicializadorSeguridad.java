package org.signbb.backend.seguridad;

import java.time.Instant;
import java.util.Locale;

import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.modelo.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Siembra el supervisor inicial al arrancar: si no existe ningún
 * MEDICO_SUPERVISOR, crea uno con {@code signbb.seguridad.supervisor-inicial.*}
 * (variables {@code SIGNBB_SUPERVISOR_CORREO} y
 * {@code SIGNBB_SUPERVISOR_CONTRASENA}). Ese supervisor crea los médicos por
 * {@code POST /api/usuarios}. Si ya hay uno, no hace nada.
 */
@Component
public class InicializadorSeguridad implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorSeguridad.class);

    private final RepositorioUsuario usuarios;
    private final PasswordEncoder codificador;
    private final String nombre;
    private final String correo;
    private final String contrasena;

    /** Inyecta el repositorio, el codificador y los datos del supervisor inicial. */
    public InicializadorSeguridad(RepositorioUsuario usuarios, PasswordEncoder codificador,
            @Value("${signbb.seguridad.supervisor-inicial.nombre:Supervisor SignBB}") String nombre,
            @Value("${signbb.seguridad.supervisor-inicial.correo}") String correo,
            @Value("${signbb.seguridad.supervisor-inicial.contrasena}") String contrasena) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.nombre = nombre;
        this.correo = correo.trim().toLowerCase(Locale.ROOT);
        this.contrasena = contrasena;
    }

    @Override
    public void run(String... args) {
        if (usuarios.existsByRol(Rol.MEDICO_SUPERVISOR)) {
            return;
        }
        UsuarioEntity u = new UsuarioEntity();
        u.setNombre(nombre);
        u.setCorreo(correo);
        u.setHashContrasena(codificador.encode(contrasena));
        u.setRol(Rol.MEDICO_SUPERVISOR);
        u.setActivo(true);
        u.setCreadoEn(Instant.now());
        usuarios.save(u);
        log.warn("Se creó el supervisor inicial {}. Cambia su contraseña (SIGNBB_SUPERVISOR_CONTRASENA) "
                + "fuera de desarrollo.", correo);
    }
}
