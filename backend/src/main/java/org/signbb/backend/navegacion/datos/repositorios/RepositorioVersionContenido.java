package org.signbb.backend.navegacion.datos.repositorios;

import java.time.Instant;
import java.util.List;

import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a las versiones del contenido clínico. */
public interface RepositorioVersionContenido extends JpaRepository<VersionContenidoEntity, Long> {

    /** Id y fecha de una versión, sin traer el grafo ni las reglas. */
    interface Resumen {
        Long getId();

        Instant getCreadaEn();
    }

    /** Id de la versión más reciente, o {@code null} si todavía no hay ninguna. */
    @Query("select max(v.id) from VersionContenidoEntity v")
    Long idMasReciente();

    /** Id y fecha de todas las versiones, de la más reciente a la más antigua. */
    @Query("select v.id as id, v.creadaEn as creadaEn from VersionContenidoEntity v order by v.id desc")
    List<Resumen> resumenes();
}
