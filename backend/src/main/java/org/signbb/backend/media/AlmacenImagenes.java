package org.signbb.backend.media;

import java.util.Optional;

/** Dónde se guardan los archivos de las imágenes. */
public interface AlmacenImagenes {

    /** Imagen leída del almacén. */
    record Imagen(byte[] datos, String tipo) {
    }

    /**
     * Guarda una imagen con ese nombre.
     *
     * @throws AlmacenNoDisponibleException si el almacén no responde
     */
    void guardar(String nombre, byte[] datos, String tipo);

    /**
     * Lee una imagen; vacío si no existe.
     *
     * @throws AlmacenNoDisponibleException si el almacén no responde
     */
    Optional<Imagen> leer(String nombre);
}
