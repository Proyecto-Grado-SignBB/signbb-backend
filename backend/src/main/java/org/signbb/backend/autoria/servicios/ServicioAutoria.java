package org.signbb.backend.autoria.servicios;

import java.util.List;

import org.signbb.backend.autoria.controladores.CategoriaDTO;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.ResumenSubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.seguridad.modelo.UsuarioActual;

/**
 * Fachada del módulo de autoría que usa la API REST. Las operaciones que
 * editan devuelven el borrador completo. Un id inexistente lanza
 * {@code RecursoNoEncontradoException} (404); una edición no válida,
 * {@link EdicionInvalidaException} (422). Quién puede leer o editar cada
 * borrador lo revisa {@link GuardiaAutoria} en el controlador, antes de
 * llamar aquí.
 */
public interface ServicioAutoria {

    /** Categorías del grafo raíz en las que se puede publicar. */
    List<CategoriaDTO> categorias();

    /**
     * Borradores del último editado al primero: los del propio médico, o
     * todos si es supervisor.
     */
    List<ResumenSubgrafoDTO> listar(UsuarioActual usuario);

    /**
     * Crea un borrador con el lienzo vacío. El autor, su institución y su
     * cuenta salen del usuario; los de la petición se ignoran.
     */
    SubgrafoDTO crear(SubgrafoRequest datos, UsuarioActual usuario);

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
     * Despublica un subgrafo: las cuidadoras que empiecen un recorrido nuevo ya
     * no lo ven y el borrador vuelve a {@code BORRADOR} con el lienzo intacto.
     * También retira su base de reglas y devuelve el borrador de reglas a
     * {@code BORRADOR}, conservando el documento. Los recorridos que ya estaban
     * en curso terminan con la versión con la que empezaron.
     *
     * @throws EdicionInvalidaException   si el subgrafo no está publicado (422)
     * @throws ContenidoInvalidoException si el contenido restante no es válido, por
     *                                    ejemplo si era el único publicado (422)
     */
    SubgrafoDTO despublicar(Long id);

    /**
     * Descarta los cambios sin publicar: el borrador vuelve a como estaba en su
     * última publicación. Si no tiene cambios sin publicar, lo devuelve igual.
     *
     * @throws EdicionInvalidaException si nunca se publicó o no tiene copia de lo publicado (422)
     */
    SubgrafoDTO descartarCambios(Long id);
}
