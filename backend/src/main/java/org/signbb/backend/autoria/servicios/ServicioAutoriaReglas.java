package org.signbb.backend.autoria.servicios;

import java.util.List;

import org.signbb.backend.autoria.controladores.BaseReglaDTO;
import org.signbb.backend.autoria.controladores.BaseReglaRequest;
import org.signbb.backend.autoria.controladores.CatalogoDTO;
import org.signbb.backend.autoria.controladores.ReglaRequest;
import org.signbb.backend.autoria.controladores.ResumenBaseReglaDTO;
import org.signbb.backend.autoria.modelo.Aviso;

/**
 * Fachada del módulo de autoría de reglas que usa la API REST. Las operaciones
 * que editan devuelven el borrador completo. Un id inexistente lanza
 * {@code RecursoNoEncontradoException} (404); una edición no válida,
 * {@link EdicionInvalidaException} (422).
 */
public interface ServicioAutoriaReglas {

    /** Borradores de bases de reglas, del último editado al primero. */
    List<ResumenBaseReglaDTO> listar();

    /** Crea un borrador vacío para un grafo. */
    BaseReglaDTO crear(BaseReglaRequest datos);

    /** Borrador completo. */
    BaseReglaDTO obtener(Long id);

    /** Cambia el nombre, el autor o la institución; el grafo no cambia. */
    BaseReglaDTO actualizar(Long id, BaseReglaRequest datos);

    /** Borra un borrador que nunca se publicó. */
    void eliminar(Long id);

    /** Agrega una regla al borrador. */
    BaseReglaDTO agregarRegla(Long id, ReglaRequest regla);

    /** Edita una regla del borrador. */
    BaseReglaDTO editarRegla(Long id, String reglaId, ReglaRequest regla);

    /** Elimina una regla del borrador. */
    BaseReglaDTO eliminarRegla(Long id, String reglaId);

    /** Avisos del borrador. */
    List<Aviso> avisos(Long id);

    /** Publica el borrador como la base de reglas activa de su grafo. */
    BaseReglaDTO publicar(Long id);

    /** Descarta los cambios sin publicar y vuelve a la última versión publicada. */
    BaseReglaDTO descartarCambios(Long id);

    /** Catálogo de nodos y respuestas de un grafo, para el editor de reglas. */
    CatalogoDTO catalogo(String grafoId);
}
