package org.signbb.backend.seguridad.datos.repositorios;

import java.util.List;
import java.util.Optional;

import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.modelo.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso a las cuentas de usuario. */
@Repository
public interface RepositorioUsuario extends JpaRepository<UsuarioEntity, Long> {

    /** La cuenta con ese correo (ya en minúsculas), si existe. */
    Optional<UsuarioEntity> findByCorreo(String correo);

    /** True si ya hay una cuenta con ese correo (ya en minúsculas). */
    boolean existsByCorreo(String correo);

    /** True si existe al menos una cuenta con ese rol. */
    boolean existsByRol(Rol rol);

    /** Las cuentas con alguno de esos roles, ordenadas por nombre. */
    List<UsuarioEntity> findByRolInOrderByNombreAsc(List<Rol> roles);
}
