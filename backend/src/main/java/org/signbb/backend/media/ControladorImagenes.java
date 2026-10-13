package org.signbb.backend.media;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subir y ver las imágenes de las preguntas.
 *
 * <ul>
 * <li>{@code POST /api/autoria/imagenes} (campo {@code archivo}): guarda la
 * imagen y responde 201 con su nombre. 422 si no es válida, 503 si Silo no
 * responde.</li>
 * <li>{@code GET /media/{nombre}}: entrega la imagen (la ven médico y
 * cuidadora). 404 si no existe.</li>
 * </ul>
 */
@RestController
public class ControladorImagenes {

    private final ServicioImagenes servicio;

    /** Inyecta el servicio de imágenes. */
    public ControladorImagenes(ServicioImagenes servicio) {
        this.servicio = servicio;
    }

    /** Sube una imagen. */
    @PostMapping("/api/autoria/imagenes")
    public ResponseEntity<ImagenSubidaDTO> subir(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        String nombre = servicio.subir(archivo.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImagenSubidaDTO(nombre));
    }

    /** Entrega una imagen. El nombre lleva un uuid y nunca cambia de contenido, así que se puede guardar en caché. */
    @GetMapping("/media/{nombre:.+}")
    public ResponseEntity<byte[]> ver(@PathVariable String nombre) {
        AlmacenImagenes.Imagen imagen = servicio.obtener(nombre);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.tipo()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(imagen.datos());
    }
}
