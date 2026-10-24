package org.signbb.backend.config;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.signbb.backend.autoria.controladores.ErrorAutoriaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Red de seguridad de los errores: atiende lo que ningún otro manejador
 * atendió y siempre responde con el cuerpo {@code {mensaje, avisos}} de
 * {@link ErrorAutoriaDTO}, con un texto corto en español y nunca con el texto
 * técnico de la excepción.
 *
 * <ul>
 * <li>Datos que no cumplen los límites de un campo ({@code @Valid}): 422 con el
 * mensaje de cada campo.</li>
 * <li>Petición mal hecha (JSON mal formado, tipo incorrecto): 400.</li>
 * <li>Choque con la base de datos (por ejemplo un correo repetido en dos
 * peticiones a la vez): 409.</li>
 * <li>Errores de Spring MVC que traen su propio código (falta un parámetro,
 * método no permitido, formato no soportado, ruta que no existe): ese código.</li>
 * <li>Cualquier otro error: 500 con un mensaje genérico; la excepción completa
 * queda en el registro del servidor.</li>
 * </ul>
 *
 * <p>Tiene la prioridad más baja a propósito: los manejadores específicos
 * ({@code ManejadorErroresAutoria}, {@code ManejadorErroresSeguridad} y
 * {@code ManejadorExcepciones}) deben ganar siempre. Si algún día se usa
 * seguridad por método ({@code @PreAuthorize}), hay que dejar pasar
 * {@code AccessDeniedException} aquí para que siga siendo 403.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ManejadorErroresGenerales {

    /** HTTP 422: los datos se entendieron pero no cumplen los límites. */
    private static final int UNPROCESSABLE = 422;

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresGenerales.class);

    /** Texto para los errores 5xx: no revela nada interno. */
    static final String MENSAJE_INESPERADO =
            "Ocurrió un error inesperado. Intenta de nuevo; si sigue pasando, avisa al equipo.";

    /** Datos que no cumplen los límites de un campo: 422 con los mensajes de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorAutoriaDTO> datosInvalidos(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" "));
        log.debug("Datos inválidos: {}", mensaje);
        return ResponseEntity.status(UNPROCESSABLE).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorAutoriaDTO(mensaje.isBlank() ? mensaje(HttpStatusCode.valueOf(UNPROCESSABLE)) : mensaje,
                        List.of()));
    }

    /** Petición mal hecha: 400. */
    @ExceptionHandler({ HttpMessageNotReadableException.class, TypeMismatchException.class,
            BindException.class })
    public ResponseEntity<ErrorAutoriaDTO> peticionInvalida(Exception ex) {
        log.debug("Petición inválida: {}", ex.toString());
        return respuesta(HttpStatus.BAD_REQUEST);
    }

    /** Choque con la base de datos (dato repetido, relación rota): 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorAutoriaDTO> conflictoDeDatos(DataIntegrityViolationException ex) {
        log.warn("Choque con la base de datos: {}", ex.getMostSpecificCause().toString());
        return respuesta(HttpStatus.CONFLICT);
    }

    /**
     * Todo lo demás. Los errores de Spring MVC que ya traen su código
     * ({@link ErrorResponse}) lo conservan, con sus cabeceras (por ejemplo
     * {@code Allow} en un 405); el resto es un 500 que se registra completo.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorAutoriaDTO> inesperado(Exception ex) {
        if (ex instanceof ErrorResponse conCodigo) {
            HttpStatusCode estado = conCodigo.getStatusCode();
            if (estado.is5xxServerError()) {
                log.error("Error del servidor ({})", estado.value(), ex);
            } else {
                log.debug("Petición rechazada ({}): {}", estado.value(), ex.toString());
            }
            return ResponseEntity.status(estado).headers(conCodigo.getHeaders())
                    .contentType(MediaType.APPLICATION_JSON).body(cuerpo(estado));
        }
        log.error("Error inesperado sin manejar", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ErrorAutoriaDTO> respuesta(HttpStatus estado) {
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON).body(cuerpo(estado));
    }

    private static ErrorAutoriaDTO cuerpo(HttpStatusCode estado) {
        return new ErrorAutoriaDTO(mensaje(estado), List.of());
    }

    /** Texto corto en español según el código HTTP. */
    static String mensaje(HttpStatusCode estado) {
        return switch (estado.value()) {
            case 400 -> "La petición no es válida. Revisa los datos e intenta de nuevo.";
            case 404 -> "No se encontró lo que buscas.";
            case 405 -> "Esa acción no está permitida en esta dirección.";
            case 406, 415 -> "El formato de los datos no se acepta.";
            case 409 -> "Ese dato entra en conflicto con otro que ya existe.";
            default -> estado.is4xxClientError() ? "No se pudo procesar la petición." : MENSAJE_INESPERADO;
        };
    }
}
