package org.signbb.backend.seguridad.controladores;

import org.signbb.backend.autoria.controladores.ErrorAutoriaDTO;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.ServicioCuentas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API de sesión: registro de padres, ingreso y usuario actual. El token que
 * devuelven registro e ingreso va en {@code Authorization: Bearer <token>}.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Sesión", description = "Registro de padres, ingreso con correo y contraseña y "
        + "usuario actual. El token devuelto va en Authorization: Bearer.")
public class ControladorAuth {

    private final ServicioCuentas cuentas;

    /** Inyecta el servicio de cuentas. */
    public ControladorAuth(ServicioCuentas cuentas) {
        this.cuentas = cuentas;
    }

    /** {@code POST /api/auth/registro}: registra un padre y abre su sesión (201). */
    @Operation(summary = "Registra un padre o acudiente",
            description = "Crea la cuenta con rol PADRE y devuelve la sesión abierta. "
                    + "Las cuentas clínicas las crea un supervisor en /api/usuarios.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada y sesión abierta"),
            @ApiResponse(responseCode = "422", description = "Falta un dato, el correo ya existe o la "
                    + "contraseña tiene menos de 8 caracteres",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/registro")
    public ResponseEntity<SesionDTO> registro(@RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentas.registrarPadre(request));
    }

    /** {@code POST /api/auth/login}: abre una sesión. */
    @Operation(summary = "Ingresa con correo y contraseña",
            description = "Devuelve el token, cuándo vence y la cuenta.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión abierta"),
            @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos, o cuenta "
                    + "inactiva (mismo mensaje en todos los casos)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/login")
    public SesionDTO login(@RequestBody LoginRequest request) {
        return cuentas.ingresar(request);
    }

    /** {@code GET /api/auth/yo}: la cuenta del usuario actual. */
    @Operation(summary = "Usuario actual", description = "La cuenta dueña del token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La cuenta"),
            @ApiResponse(responseCode = "401", description = "Sin token, token inválido o cuenta inactiva",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @GetMapping("/yo")
    public UsuarioDTO yo(UsuarioActual usuario) {
        return cuentas.yo(usuario);
    }
}
