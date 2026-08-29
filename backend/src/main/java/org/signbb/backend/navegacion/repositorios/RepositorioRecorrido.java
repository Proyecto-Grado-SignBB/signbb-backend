package org.signbb.backend.navegacion.repositorios;

import org.signbb.backend.navegacion.servicios.Recorrido;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositorioRecorrido extends CrudRepository<Recorrido, Long> {
}
