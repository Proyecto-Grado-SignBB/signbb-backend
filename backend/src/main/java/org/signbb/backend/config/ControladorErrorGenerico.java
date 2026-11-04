package org.signbb.backend.config;

import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.signbb.backend.autoria.controladores.ErrorAutoriaDTO;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Última red de seguridad para errores. Atiende lo que el servlet container
 * manda a {@code /error} porque se escapó de todo lo demás: de los
 * manejadores de {@code ConfiguracionSeguridad} (que solo traducen
 * {@code AuthenticationException}/{@code AccessDeniedException}) y de
 * {@link ManejadorErroresGenerales} (que solo ve lo que pasa dentro del
 * controlador). El caso típico es una falla de infraestructura (por ejemplo,
 * la base de datos caída) durante la validación del token, dentro de los
 * filtros de seguridad, antes de que la petición llegue al controlador.
 *
 * <p>Sin este controlador, esos casos responden con la página o el JSON por
 * defecto de Spring Boot ({@code {timestamp, status, error, path}}), que no
 * sigue el contrato del resto de la app. Con él, responden igual que
 * cualquier otro error: {@code {mensaje, avisos}} en español, sin detalles
 * técnicos.</p>
 */
@RestController
public class ControladorErrorGenerico implements ErrorController {

    /** {@code /error}: lo arma el servlet container, nunca se llama directo. */
    @RequestMapping("/error")
    public ResponseEntity<ErrorAutoriaDTO> error(HttpServletRequest peticion) {
        Object codigo = peticion.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatusCode estado = codigo instanceof Integer valor
                ? HttpStatusCode.valueOf(valor)
                : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorAutoriaDTO(ManejadorErroresGenerales.mensaje(estado), List.of()));
    }
}
