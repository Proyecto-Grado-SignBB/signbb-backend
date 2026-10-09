package org.signbb.backend.autoria.servicios;

import java.util.List;

import org.signbb.backend.autoria.modelo.Aviso;

/**
 * El borrador no se puede publicar: tiene avisos bloqueantes o el contenido
 * compuesto (raíz + subgrafos) no pasa la validación del grafo clínico. La API
 * lo responde como 422 con la lista de avisos.
 */
public class ContenidoInvalidoException extends RuntimeException {

    private final List<Aviso> avisos;

    /** Crea la excepción con un mensaje para el médico y los avisos que la causan. */
    public ContenidoInvalidoException(String mensaje, List<Aviso> avisos) {
        super(mensaje);
        this.avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }

    /** Avisos que impiden publicar (puede estar vacía). */
    public List<Aviso> getAvisos() {
        return avisos;
    }
}
