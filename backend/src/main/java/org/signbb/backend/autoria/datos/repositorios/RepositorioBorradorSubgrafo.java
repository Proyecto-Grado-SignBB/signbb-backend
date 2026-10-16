package org.signbb.backend.autoria.datos.repositorios;

import java.util.List;
import java.util.Optional;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los borradores de subgrafo. */
public interface RepositorioBorradorSubgrafo extends JpaRepository<BorradorSubgrafoEntity, Long> {

    /** True si ya hay un borrador con ese {@code grafoId}. */
    boolean existsByGrafoId(String grafoId);

    /** Borrador de subgrafo con ese {@code grafoId}, si existe. */
    Optional<BorradorSubgrafoEntity> findByGrafoId(String grafoId);

    /** Borradores de un autor, en el orden pedido. */
    List<BorradorSubgrafoEntity> findByAutorId(Long autorId, Sort orden);
}
