package org.signbb.backend.media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Dónde se guardan los archivos de las imágenes. */
public interface AlmacenImagenes {

    /** Imagen leída del almacén. */
    record Imagen(byte[] datos, String tipo) {
    }

    /**
     * Archivo que hay en el almacén.
     *
     * @param nombre       nombre con el que se guardó
     * @param modificadaEn cuándo se subió
     */
    record Guardada(String nombre, Instant modificadaEn) {
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

    /**
     * Lista todo lo que hay guardado (nombre y fecha de subida), sin leer los archivos.
     *
     * @throws AlmacenNoDisponibleException si el almacén no responde
     */
    List<Guardada> listar();

    /**
     * Borra una imagen; si no existe no hace nada.
     *
     * @throws AlmacenNoDisponibleException si el almacén no responde
     */
    void eliminar(String nombre);
}
