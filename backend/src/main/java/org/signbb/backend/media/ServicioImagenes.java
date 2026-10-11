package org.signbb.backend.media;

import java.util.Locale;
import java.util.UUID;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Reglas de las imágenes de las preguntas: formato, tamaño y nombre. */
@Service
public class ServicioImagenes {

    private final AlmacenImagenes almacen;
    private final long maxBytes;

    /** Inyecta el almacén y el tope de tamaño ({@code signbb.media.max-bytes}). */
    public ServicioImagenes(AlmacenImagenes almacen,
            @Value("${signbb.media.max-bytes:1048576}") long maxBytes) {
        this.almacen = almacen;
        this.maxBytes = maxBytes;
    }

    /**
     * Valida y guarda una imagen.
     *
     * @return el nombre con el que quedó guardada (uuid y extensión)
     * @throws ImagenInvalidaException      si está vacía, pesa de más o no es png, jpeg, webp ni gif
     * @throws AlmacenNoDisponibleException si Silo no responde
     */
    public String subir(byte[] datos) {
        if (datos == null || datos.length == 0) {
            throw new ImagenInvalidaException("El archivo está vacío.");
        }
        if (datos.length > maxBytes) {
            throw new ImagenInvalidaException("La imagen pesa " + megas(datos.length)
                    + " y el máximo es " + megas(maxBytes) + ". Elige una más liviana.");
        }
        FormatoImagen formato = FormatoImagen.detectar(datos).orElseThrow(() -> new ImagenInvalidaException(
                "Solo se aceptan imágenes png, jpeg, webp o gif."));
        String nombre = UUID.randomUUID() + "." + formato.extension();
        almacen.guardar(nombre, datos, formato.tipo());
        return nombre;
    }

    /**
     * Lee una imagen guardada.
     *
     * @throws RecursoNoEncontradoException si el nombre no es válido o no existe
     * @throws AlmacenNoDisponibleException si Silo no responde
     */
    public AlmacenImagenes.Imagen obtener(String nombre) {
        if (!FormatoImagen.nombreValido(nombre)) {
            throw new RecursoNoEncontradoException("no hay una imagen '" + nombre + "'");
        }
        return almacen.leer(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay una imagen '" + nombre + "'"));
    }

    private static String megas(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
    }
}
