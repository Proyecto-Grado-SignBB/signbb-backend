package org.signbb.backend.crobservaciones.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la auditoría de observaciones del cuidador. */
public interface RepositorioObservaciones extends JpaRepository<ObservacionEntity, Long> {

    /** Historial de un recorrido, en el orden en que ocurrió. */
    List<ObservacionEntity> findByRecorridoIdOrderByTimestampAsc(String recorridoId);
}
