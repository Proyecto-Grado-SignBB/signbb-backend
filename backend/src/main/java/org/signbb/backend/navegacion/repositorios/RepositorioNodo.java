package org.signbb.backend.navegacion.repositorios;

import org.signbb.backend.navegacion.servicios.Nodo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositorioNodo extends CrudRepository<Nodo, Long> {
}
