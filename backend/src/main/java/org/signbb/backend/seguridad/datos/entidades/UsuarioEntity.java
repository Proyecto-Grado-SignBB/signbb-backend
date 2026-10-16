package org.signbb.backend.seguridad.datos.entidades;

import java.time.Instant;

import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuenta de un usuario: padre, médico o médico supervisor. El correo se
 * guarda en minúsculas y la contraseña solo como hash BCrypt.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correo con el que ingresa, único y en minúsculas. */
    @Column(nullable = false, unique = true)
    private String correo;

    /** Nombre para mostrar; es el autor de los subgrafos y reglas que crea. */
    @Column(nullable = false)
    private String nombre;

    /** Hash BCrypt de la contraseña. */
    @Column(nullable = false)
    private String hashContrasena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    /** Institución del médico, o {@code null} para un padre. */
    private String institucion;

    /** Una cuenta inactiva no puede ingresar y sus tokens dejan de servir. */
    @Column(nullable = false)
    private boolean activo = true;

    private Instant creadoEn;

    /** El usuario de la petición en curso que corresponde a esta cuenta. */
    public UsuarioActual comoActual() {
        return new UsuarioActual(id, nombre, correo, rol, institucion);
    }
}
