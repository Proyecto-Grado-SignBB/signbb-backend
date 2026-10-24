package org.signbb.backend.seguridad.controladores;

import java.util.List;

import org.signbb.backend.autoria.controladores.ErrorAutoriaDTO;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.CuentaInvalidaException;
import org.signbb.backend.seguridad.servicios.ServicioCuentas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * API de cuentas clínicas, solo para supervisores (la regla está en {@code
 * ConfiguracionSeguridad}): crear médicos y supervisores, listarlos y
 * activarlos o desactivarlos.
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Cuentas clínicas (solo MEDICO_SUPERVISOR): crear médicos "
        + "y supervisores, listarlos y activarlos o desactivarlos.")
public class ControladorUsuarios {

    private final ServicioCuentas cuentas;

    /** Inyecta el servicio de cuentas. */
    public ControladorUsuarios(ServicioCuentas cuentas) {
        this.cuentas = cuentas;
    }

    /** {@code POST /api/usuarios}: crea una cuenta clínica (201). */
    @Operation(summary = "Crea una cuenta clínica",
            description = "El rol debe ser MEDICO o MEDICO_SUPERVISOR.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada"),
            @ApiResponse(responseCode = "403", description = "Quien llama no es supervisor",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "Rol PADRE, correo repetido, contraseña corta "
                    + "o falta un dato",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody NuevoUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentas.crearClinico(request));
    }

    /** {@code GET /api/usuarios}: cuentas clínicas por nombre. */
    @Operation(summary = "Lista las cuentas clínicas", description = "Médicos y supervisores, por nombre.")
    @ApiResponse(responseCode = "200", description = "Las cuentas")
    @GetMapping
    public List<UsuarioDTO> listar() {
        return cuentas.listarClinicos();
    }

    /** {@code PUT /api/usuarios/{id}/activo}: activa o desactiva una cuenta. */
    @Operation(summary = "Activa o desactiva una cuenta",
            description = "Una cuenta inactiva no puede ingresar y sus tokens dejan de servir. "
                    + "Un supervisor no puede desactivarse a sí mismo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta actualizada"),
            @ApiResponse(responseCode = "404", description = "La cuenta no existe", content = @Content),
            @ApiResponse(responseCode = "422", description = "Falta activo o es la propia cuenta",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PutMapping("/{id}/activo")
    public UsuarioDTO cambiarActivo(@PathVariable Long id, @RequestBody ActivoRequest request,
            UsuarioActual usuario) {
        if (request.activo() == null) {
            throw new CuentaInvalidaException("Indica si la cuenta queda activa (true o false).");
        }
        return cuentas.cambiarActivo(id, request.activo(), usuario);
    }
}
