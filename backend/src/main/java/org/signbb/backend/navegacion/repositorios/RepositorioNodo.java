package org.signbb.backend.navegacion.repositorios;

import org.signbb.backend.navegacion.servicios.Nodo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Acceso JPA a los nodos persistidos. */
@Repository
public interface RepositorioNodo extends CrudRepository<Nodo, Long> {
}
