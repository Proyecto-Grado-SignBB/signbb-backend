package org.signbb.backend.observaciones.datos.repositorios;

import java.util.List;

import org.signbb.backend.observaciones.datos.entidades.RecorridoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los recorridos guardados con su dueño. */
public interface RepositorioRecorridos extends JpaRepository<RecorridoEntity, String> {

    /** Recorridos de un padre, del más nuevo al más viejo. */
    List<RecorridoEntity> findByUsuarioIdOrderByIniciadoEnDesc(Long usuarioId);
}
