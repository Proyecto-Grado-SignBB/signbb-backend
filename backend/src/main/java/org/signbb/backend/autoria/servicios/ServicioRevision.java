package org.signbb.backend.autoria.servicios;

import java.util.List;

import org.signbb.backend.autoria.controladores.ResumenSubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.seguridad.modelo.UsuarioActual;

/**
 * Revisión de un subgrafo antes de publicarlo. El autor lo envía; un
 * supervisor que no sea el autor lo aprueba, y así se publica, o lo rechaza
 * con un comentario. Mientras está en revisión nadie lo edita.
 */
public interface ServicioRevision {

    /**
     * Envía el subgrafo a revisión.
     *
     * @throws ContenidoInvalidoException si tiene avisos bloqueantes (422 con los avisos)
     * @throws EdicionInvalidaException   si ya está en revisión o no tiene cambios que revisar
     */
    SubgrafoDTO enviar(Long id);

    /**
     * Retira el subgrafo de revisión: vuelve a borrador, o a cambios sin
     * publicar si ya tenía una publicación.
     *
     * @throws EdicionInvalidaException si no está en revisión
     */
    SubgrafoDTO retirar(Long id);

    /** Subgrafos en revisión de otros autores, del enviado hace más tiempo al más reciente. */
    List<ResumenSubgrafoDTO> pendientes(UsuarioActual supervisor);

    /**
     * Aprueba y publica el subgrafo con su base de reglas.
     *
     * @throws EdicionInvalidaException si no está en revisión
     * @throws org.signbb.backend.seguridad.servicios.AccesoDenegadoException si el supervisor es el autor
     */
    SubgrafoDTO aprobar(Long id, UsuarioActual supervisor);

    /**
     * Rechaza el subgrafo con un comentario para el autor, que lo puede
     * corregir y volver a enviar.
     *
     * @throws EdicionInvalidaException si falta el comentario o no está en revisión
     * @throws org.signbb.backend.seguridad.servicios.AccesoDenegadoException si el supervisor es el autor
     */
    SubgrafoDTO rechazar(Long id, String comentario, UsuarioActual supervisor);
}
