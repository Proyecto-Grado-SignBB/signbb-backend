package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.servicios.ContenidoInvalidoException;
import org.signbb.backend.autoria.servicios.EdicionInvalidaException;
import org.signbb.backend.media.AlmacenNoDisponibleException;
import org.signbb.backend.media.ControladorImagenes;
import org.signbb.backend.media.ImagenInvalidaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores del módulo de autoría. A diferencia de {@code ManejadorExcepciones},
 * responden con cuerpo ({@link ErrorAutoriaDTO}) porque el mensaje es para el
 * médico. También cubre las imágenes ({@link ControladorImagenes}). El 404
 * sigue a cargo de {@code ManejadorExcepciones}.
 */
@RestControllerAdvice(assignableTypes = { ControladorAutoria.class, ControladorReglas.class,
        ControladorImagenes.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorErroresAutoria {

    /** HTTP 422: la petición se entendió pero el contenido no es válido. */
    private static final int UNPROCESSABLE = 422;

    /** Edición no válida: 422. */
    @ExceptionHandler(EdicionInvalidaException.class)
    public ResponseEntity<ErrorAutoriaDTO> edicionInvalida(EdicionInvalidaException ex) {
        return ResponseEntity.status(UNPROCESSABLE).body(new ErrorAutoriaDTO(ex.getMessage(), List.of()));
    }

    /** Borrador que no se puede publicar: 422 con los avisos. */
    @ExceptionHandler(ContenidoInvalidoException.class)
    public ResponseEntity<ErrorAutoriaDTO> contenidoInvalido(ContenidoInvalidoException ex) {
        return ResponseEntity.status(UNPROCESSABLE).body(new ErrorAutoriaDTO(ex.getMessage(), ex.getAvisos()));
    }

    /** Imagen que no se puede aceptar (formato, tamaño): 422. */
    @ExceptionHandler(ImagenInvalidaException.class)
    public ResponseEntity<ErrorAutoriaDTO> imagenInvalida(ImagenInvalidaException ex) {
        return ResponseEntity.status(UNPROCESSABLE).body(new ErrorAutoriaDTO(ex.getMessage(), List.of()));
    }

    /** Silo no responde: 503, transitorio. */
    @ExceptionHandler(AlmacenNoDisponibleException.class)
    public ResponseEntity<ErrorAutoriaDTO> almacenNoDisponible(AlmacenNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorAutoriaDTO(
                "El almacén de imágenes no responde. Intenta de nuevo en un momento.", List.of()));
    }

    /** Dos ediciones simultáneas del mismo borrador: 409. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorAutoriaDTO> conflicto(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorAutoriaDTO(
                "Otra persona cambió este subgrafo al mismo tiempo: recarga para ver sus cambios.", List.of()));
    }
}
