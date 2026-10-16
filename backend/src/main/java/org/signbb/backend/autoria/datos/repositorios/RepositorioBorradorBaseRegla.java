package org.signbb.backend.autoria.datos.repositorios;

import java.util.List;
import java.util.Optional;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los borradores de base de reglas. */
public interface RepositorioBorradorBaseRegla extends JpaRepository<BorradorBaseReglaEntity, Long> {

    /** Borrador de reglas de un grafo, si existe. */
    Optional<BorradorBaseReglaEntity> findByGrafoId(String grafoId);

    /** True si ya hay un borrador de reglas para ese {@code grafoId}. */
    boolean existsByGrafoId(String grafoId);

    /** Bases de reglas de un autor, en el orden pedido. */
    List<BorradorBaseReglaEntity> findByAutorId(Long autorId, Sort orden);
}
