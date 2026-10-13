package org.signbb.backend.autoria.servicios;

import java.util.List;

import org.signbb.backend.autoria.controladores.CategoriaDTO;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.ResumenSubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.modelo.Aviso;

/**
 * Fachada del módulo de autoría que usa la API REST. Las operaciones que
 * editan devuelven el borrador completo. Un id inexistente lanza
 * {@code RecursoNoEncontradoException} (404); una edición no válida,
 * {@link EdicionInvalidaException} (422).
 */
public interface ServicioAutoria {

    /** Categorías del grafo raíz en las que se puede publicar. */
    List<CategoriaDTO> categorias();

    /** Borradores, del último editado al primero. */
    List<ResumenSubgrafoDTO> listar();

    /** Crea un borrador con el lienzo vacío. */
    SubgrafoDTO crear(SubgrafoRequest datos);

    /** Borrador completo. */
    SubgrafoDTO obtener(Long id);

    /** Cambia el nombre, la categoría, el texto de la opción, el autor o la institución. */
    SubgrafoDTO actualizar(Long id, SubgrafoRequest datos);

    /** Borra un borrador que nunca se publicó. */
    void eliminar(Long id);

    /** El "+": crea un nodo después de una respuesta, o la primera pregunta. */
    SubgrafoDTO agregarNodo(Long id, NodoRequest nodo);

    /** Lleva una respuesta a una pregunta existente ({@code nodoId} nulo la vuelve "+"). */
    SubgrafoDTO enlazar(Long id, String respuestaId, String nodoId);

    /** Edita un nodo. */
    SubgrafoDTO editarNodo(Long id, String nodoId, NodoRequest nodo);

    /** Elimina un nodo y su rama. */
    SubgrafoDTO eliminarNodo(Long id, String nodoId);

    /** Avisos del lienzo. */
    List<Aviso> avisos(Long id);

    /**
     * Publica el borrador.
     *
     * @throws ContenidoInvalidoException si no se puede publicar (422 con los avisos)
     */
    SubgrafoDTO publicar(Long id);

    /**
     * Descarta los cambios sin publicar: el borrador vuelve a como estaba en su
     * última publicación. Si no tiene cambios sin publicar, lo devuelve igual.
     *
     * @throws EdicionInvalidaException si nunca se publicó o no tiene copia de lo publicado (422)
     */
    SubgrafoDTO descartarCambios(Long id);
}
