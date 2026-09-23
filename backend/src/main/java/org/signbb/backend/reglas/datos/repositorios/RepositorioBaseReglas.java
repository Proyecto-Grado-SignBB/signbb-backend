package org.signbb.backend.reglas.datos.repositorios;

import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las bases de reglas persistidas. */
public interface RepositorioBaseReglas extends JpaRepository<BaseReglasEntity, Long> {
}
