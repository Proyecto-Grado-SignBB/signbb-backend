package org.signbb.backend.navegacion.repositorios;

import org.signbb.backend.navegacion.servicios.Grafo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositorioGrafo extends CrudRepository<Grafo, Long> {
}
