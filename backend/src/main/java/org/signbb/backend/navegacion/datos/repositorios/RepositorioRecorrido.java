package org.signbb.backend.navegacion.datos.repositorios;

import org.signbb.backend.navegacion.servicios.Recorrido;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Acceso JPA a los recorridos persistidos. */
@Repository
public interface RepositorioRecorrido extends CrudRepository<Recorrido, Long> {
}
