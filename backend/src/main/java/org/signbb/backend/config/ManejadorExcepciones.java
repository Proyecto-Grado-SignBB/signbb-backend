package org.signbb.backend.config;

import java.util.List;

import org.signbb.backend.autoria.controladores.ErrorAutoriaDTO;
import org.signbb.backend.cache.CacheNoDisponibleException;
import org.signbb.backend.navegacion.modelo.RecorridoOcupadoException;
import org.signbb.backend.observaciones.servicios.PreguntaDesactualizadaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Errores específicos del backend. Va antes que {@link ManejadorErroresGenerales},
 * que es la red de seguridad de lo que nadie más atiende.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ManejadorExcepciones {

    /** HTTP 413: el archivo supera el límite de subida. */
    private static final int PAYLOAD_TOO_LARGE = 413;

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Void> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * MODIFICADO: agregado para que las opciones inválidas del cuidador
     * (validadas en {@code ServicioCapturaImpl}) respondan 400 en vez de
     * caer como 500 sin manejar. Sigue el mismo patrón que el handler de
     * arriba: sin cuerpo, solo el status.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    /**
     * Archivo subido por encima del límite del servidor (imágenes de las
     * preguntas). Este error ocurre antes de llegar al controlador, por eso
     * no lo atiende {@code ManejadorErroresAutoria}.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorAutoriaDTO> manejarArchivoGrande(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(PAYLOAD_TOO_LARGE)
                .body(new ErrorAutoriaDTO("El archivo es demasiado grande. El máximo es 1 MB.", List.of()));
    }

    /** Otra operación está cambiando el mismo recorrido: 409 para que el cliente espere y reintente. */
    @ExceptionHandler(RecorridoOcupadoException.class)
    public ResponseEntity<ErrorAutoriaDTO> manejarRecorridoOcupado(RecorridoOcupadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorAutoriaDTO(ex.getMessage(), List.of()));
    }

    /** La respuesta era para una pregunta que ya no es la actual: 409 y el cliente vuelve a consultar. */
    @ExceptionHandler(PreguntaDesactualizadaException.class)
    public ResponseEntity<ErrorAutoriaDTO> manejarPreguntaDesactualizada(PreguntaDesactualizadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorAutoriaDTO(ex.getMessage(), List.of()));
    }

    /**
     * MODIFICADO (cache): Redis no disponible para sesiones — 503 para que
     * el cliente sepa que es transitorio y reintente; degradar a memoria
     * crearía inconsistencias entre sesiones.
     */
    @ExceptionHandler(CacheNoDisponibleException.class)
    public ResponseEntity<Void> manejarCacheNoDisponible(CacheNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}