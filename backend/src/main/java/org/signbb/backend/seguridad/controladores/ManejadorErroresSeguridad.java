package org.signbb.backend.seguridad.controladores;

import java.util.List;

import org.signbb.backend.autoria.controladores.ErrorAutoriaDTO;
import org.signbb.backend.seguridad.servicios.AccesoDenegadoException;
import org.signbb.backend.seguridad.servicios.CredencialesInvalidasException;
import org.signbb.backend.seguridad.servicios.CuentaInvalidaException;
import org.signbb.backend.seguridad.servicios.IntentosExcedidosException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores de seguridad a HTTP para todos los controladores, con
 * el mismo cuerpo {@code {mensaje, avisos}} de la autoría: 401 si no se pudo
 * identificar al usuario, 403 si no puede hacer la acción y 422 si los datos
 * de la cuenta no sirven, y 429 si el ingreso está bloqueado por intentos fallidos.
 */
@RestControllerAdvice
public class ManejadorErroresSeguridad {

    /** HTTP 422: los datos de la cuenta no se pueden aceptar. */
    private static final int UNPROCESSABLE = 422;

    /** 401: credenciales malas, cuenta inactiva o sesión vencida. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorAutoriaDTO> credenciales(CredencialesInvalidasException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** 403: el usuario no puede hacer esa acción. */
    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorAutoriaDTO> accesoDenegado(AccesoDenegadoException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** 422: correo repetido, contraseña corta, rol no permitido. */
    @ExceptionHandler(CuentaInvalidaException.class)
    public ResponseEntity<ErrorAutoriaDTO> cuentaInvalida(CuentaInvalidaException e) {
        return ResponseEntity.status(UNPROCESSABLE).body(new ErrorAutoriaDTO(e.getMessage(), List.of()));
    }

    /** 429: demasiados intentos fallidos de ingreso; {@code Retry-After} trae los segundos de espera. */
    @ExceptionHandler(IntentosExcedidosException.class)
    public ResponseEntity<ErrorAutoriaDTO> demasiadosIntentos(IntentosExcedidosException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getEspera().toSeconds()))
                .body(new ErrorAutoriaDTO(e.getMessage(), List.of()));
    }

    private static ResponseEntity<ErrorAutoriaDTO> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorAutoriaDTO(mensaje, List.of()));
    }
}
