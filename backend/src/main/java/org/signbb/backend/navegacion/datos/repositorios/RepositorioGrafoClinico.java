package org.signbb.backend.navegacion.datos.repositorios;

import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los grafos clínicos persistidos. */
public interface RepositorioGrafoClinico extends JpaRepository<GrafoClinicoEntity, Long> {
}
