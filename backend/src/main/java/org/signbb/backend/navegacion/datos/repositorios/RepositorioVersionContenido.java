package org.signbb.backend.navegacion.datos.repositorios;

import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las versiones del contenido clínico. */
public interface RepositorioVersionContenido extends JpaRepository<VersionContenidoEntity, Long> {

    /** Id de la versión más reciente, o {@code null} si todavía no hay ninguna. */
    @Query("select max(v.id) from VersionContenidoEntity v")
    Long idMasReciente();
}
