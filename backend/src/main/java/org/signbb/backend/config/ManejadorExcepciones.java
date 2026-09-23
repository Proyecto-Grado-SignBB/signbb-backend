package org.signbb.backend.config;

import org.signbb.backend.cache.CacheNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ManejadorExcepciones {

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
     * MODIFICADO (cache): Redis no disponible para sesiones — 503 para que
     * el cliente sepa que es transitorio y reintente; degradar a memoria
     * crearía inconsistencias entre sesiones.
     */
    @ExceptionHandler(CacheNoDisponibleException.class)
    public ResponseEntity<Void> manejarCacheNoDisponible(CacheNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}