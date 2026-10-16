package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.servicios.GuardiaAutoria;
import org.signbb.backend.autoria.servicios.ServicioRevision;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API REST de la revisión de subgrafos. El autor envía y retira; aprobar,
 * rechazar y la lista de pendientes son del supervisor (lo exige {@code
 * ConfiguracionSeguridad}), que no puede revisar lo suyo.
 */
@RestController
@RequestMapping("/api/autoria")
@Tag(name = "Revisión", description = "El médico envía su subgrafo a revisión; un supervisor que no "
        + "sea el autor lo aprueba (y se publica) o lo rechaza con un comentario.")
public class ControladorRevision {

    private final ServicioRevision servicio;
    private final GuardiaAutoria guardia;

    /** Inyecta el servicio de revisión y la guardia. */
    public ControladorRevision(ServicioRevision servicio, GuardiaAutoria guardia) {
        this.servicio = servicio;
        this.guardia = guardia;
    }

    /** {@code POST /api/autoria/subgrafos/{id}/enviar-revision}: el autor lo envía a revisión. */
    @Operation(summary = "Envía el subgrafo a revisión",
            description = "Solo el autor. Valida los avisos del lienzo y de las reglas como al publicar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo en revisión (EN_REVISION)"),
            @ApiResponse(responseCode = "403", description = "No es el autor", content = @Content),
            @ApiResponse(responseCode = "404", description = "El subgrafo no existe", content = @Content),
            @ApiResponse(responseCode = "422", description = "Hay avisos bloqueantes, ya está en revisión o "
                    + "no tiene cambios", content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/enviar-revision")
    public SubgrafoDTO enviar(@PathVariable Long id, UsuarioActual usuario) {
        guardia.autorSubgrafo(id, usuario);
        return servicio.enviar(id);
    }

    /** {@code POST /api/autoria/subgrafos/{id}/retirar-revision}: el autor lo retira para editarlo. */
    @Operation(summary = "Retira el subgrafo de revisión",
            description = "Solo el autor. Vuelve a BORRADOR, o a CAMBIOS_SIN_PUBLICAR si ya estaba publicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo fuera de revisión"),
            @ApiResponse(responseCode = "403", description = "No es el autor", content = @Content),
            @ApiResponse(responseCode = "404", description = "El subgrafo no existe", content = @Content),
            @ApiResponse(responseCode = "422", description = "No está en revisión",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/retirar-revision")
    public SubgrafoDTO retirar(@PathVariable Long id, UsuarioActual usuario) {
        guardia.autorSubgrafo(id, usuario);
        return servicio.retirar(id);
    }

    /** {@code GET /api/autoria/revisiones}: subgrafos en revisión de otros autores. */
    @Operation(summary = "Lista las revisiones pendientes",
            description = "Solo supervisor. No incluye los suyos.")
    @ApiResponse(responseCode = "200", description = "Subgrafos en revisión")
    @GetMapping("/revisiones")
    public List<ResumenSubgrafoDTO> pendientes(UsuarioActual usuario) {
        return servicio.pendientes(usuario);
    }

    /** {@code POST /api/autoria/subgrafos/{id}/aprobar}: aprueba y publica. */
    @Operation(summary = "Aprueba y publica el subgrafo",
            description = "Solo un supervisor que no sea el autor. Publica el subgrafo y su base de "
                    + "reglas en una sola acción.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo publicado"),
            @ApiResponse(responseCode = "403", description = "Es el autor", content = @Content),
            @ApiResponse(responseCode = "404", description = "El subgrafo no existe", content = @Content),
            @ApiResponse(responseCode = "422", description = "No está en revisión o no se puede publicar",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/aprobar")
    public SubgrafoDTO aprobar(@PathVariable Long id, UsuarioActual usuario) {
        return servicio.aprobar(id, usuario);
    }

    /** {@code POST /api/autoria/subgrafos/{id}/rechazar}: lo devuelve al autor con un comentario. */
    @Operation(summary = "Rechaza el subgrafo",
            description = "Solo un supervisor que no sea el autor. El comentario es obligatorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo devuelto al autor con el comentario"),
            @ApiResponse(responseCode = "403", description = "Es el autor", content = @Content),
            @ApiResponse(responseCode = "404", description = "El subgrafo no existe", content = @Content),
            @ApiResponse(responseCode = "422", description = "Falta el comentario o no está en revisión",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/rechazar")
    public SubgrafoDTO rechazar(@PathVariable Long id, @RequestBody(required = false) RechazoRequest rechazo,
            UsuarioActual usuario) {
        return servicio.rechazar(id, rechazo == null ? null : rechazo.comentario(), usuario);
    }
}
