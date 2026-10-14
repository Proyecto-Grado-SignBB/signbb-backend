package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.servicios.ServicioAutoriaReglas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API REST del panel de reglas. Una base de reglas pertenece a un grafo
 * ({@code grafo_raiz} o un subgrafo) y agrupa las reglas que lo redirigen. Las
 * operaciones que editan devuelven el borrador completo ({@link BaseReglaDTO}).
 * Errores: 404 si el recurso no existe; 422 con {@link ErrorAutoriaDTO} si la
 * edición no es válida o la base no se puede publicar; 409 si otra persona la
 * cambió al mismo tiempo.
 */
@RestController
@RequestMapping("/api/autoria/bases")
@Tag(name = "Reglas", description = "Panel de reglas del médico: arma y publica las bases "
        + "de reglas asociadas a un grafo. Las operaciones que editan devuelven el borrador "
        + "completo. Errores: 404 si el recurso no existe; 422 con {mensaje, avisos} si la "
        + "edición no es válida o la base no se puede publicar; 409 si otra persona la cambió.")
public class ControladorReglas {

    private final ServicioAutoriaReglas servicio;

    /** Inyecta el servicio de autoría de reglas. */
    public ControladorReglas(ServicioAutoriaReglas servicio) {
        this.servicio = servicio;
    }

    /** {@code GET /api/autoria/bases}: tablero de borradores de reglas. */
    @Operation(summary = "Lista los borradores de reglas",
            description = "Del último editado al primero.")
    @ApiResponse(responseCode = "200", description = "Resumen de cada borrador")
    @GetMapping
    public List<ResumenBaseReglaDTO> listar() {
        return servicio.listar();
    }

    /** {@code GET /api/autoria/bases/catalogo}: nodos y respuestas de un grafo. */
    @Operation(summary = "Catálogo de nodos de un grafo",
            description = "Nodos y respuestas válidos para el editor de reglas. Para un subgrafo "
                    + "son ids locales; para el grafo raíz vienen prefijados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo del grafo"),
            @ApiResponse(responseCode = "404", description = "El grafo no existe", content = @Content),
            @ApiResponse(responseCode = "422", description = "No se indicó el grafo", content = @Content)
    })
    @GetMapping("/catalogo")
    public CatalogoDTO catalogo(@RequestParam String grafoId) {
        return servicio.catalogo(grafoId);
    }

    /** {@code POST /api/autoria/bases}: crea un borrador vacío (201). */
    @Operation(summary = "Crea un borrador de reglas",
            description = "Crea la base vacía para el grafo indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Borrador creado"),
            @ApiResponse(responseCode = "422", description = "Datos inválidos o ya hay una base para ese grafo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping
    public ResponseEntity<BaseReglaDTO> crear(@RequestBody BaseReglaRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
    }

    /** {@code GET /api/autoria/bases/{id}}: borrador completo. */
    @Operation(summary = "Obtiene un borrador de reglas completo",
            description = "Incluye las reglas y los avisos calculados en el momento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador completo"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe", content = @Content)
    })
    @GetMapping("/{id}")
    public BaseReglaDTO obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** {@code PUT /api/autoria/bases/{id}}: cambia el nombre, el autor o la institución. */
    @Operation(summary = "Actualiza los datos de la base de reglas",
            description = "Cambia nombre, autor o institución; el grafo no cambia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "Datos inválidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PutMapping("/{id}")
    public BaseReglaDTO actualizar(@PathVariable Long id, @RequestBody BaseReglaRequest datos) {
        return servicio.actualizar(id, datos);
    }

    /** {@code DELETE /api/autoria/bases/{id}}: borra un borrador nunca publicado (204). */
    @Operation(summary = "Elimina un borrador de reglas",
            description = "Solo si nunca se publicó.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrador eliminado"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "La base ya está publicada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code POST /api/autoria/bases/{id}/reglas}: agrega una regla. */
    @Operation(summary = "Agrega una regla al borrador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado con la regla nueva"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe", content = @Content),
            @ApiResponse(responseCode = "422", description = "La regla no es válida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/{id}/reglas")
    public BaseReglaDTO agregarRegla(@PathVariable Long id, @RequestBody ReglaRequest regla) {
        return servicio.agregarRegla(id, regla);
    }

    /** {@code PUT /api/autoria/bases/{id}/reglas/{reglaId}}: edita una regla. */
    @Operation(summary = "Edita una regla del borrador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador o la regla no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "La regla no es válida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PutMapping("/{id}/reglas/{reglaId}")
    public BaseReglaDTO editarRegla(@PathVariable Long id, @PathVariable String reglaId,
            @RequestBody ReglaRequest regla) {
        return servicio.editarRegla(id, reglaId, regla);
    }

    /** {@code DELETE /api/autoria/bases/{id}/reglas/{reglaId}}: elimina una regla. */
    @Operation(summary = "Elimina una regla del borrador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador o la regla no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @DeleteMapping("/{id}/reglas/{reglaId}")
    public BaseReglaDTO eliminarRegla(@PathVariable Long id, @PathVariable String reglaId) {
        return servicio.eliminarRegla(id, reglaId);
    }

    /** {@code GET /api/autoria/bases/{id}/avisos}: avisos del borrador. */
    @Operation(summary = "Avisos del borrador de reglas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avisos actuales"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe", content = @Content)
    })
    @GetMapping("/{id}/avisos")
    public List<Aviso> avisos(@PathVariable Long id) {
        return servicio.avisos(id);
    }

    /** {@code POST /api/autoria/bases/{id}/publicar}: publica la base de reglas. */
    @Operation(summary = "Publica el borrador de reglas",
            description = "Deja la base como la activa del grafo. Las reglas de un subgrafo se publican "
                    + "publicando el subgrafo (no desde aquí); este endpoint queda para la base del grafo "
                    + "raíz. Si el lienzo del subgrafo cambió sin publicar, responde 422 pidiendo "
                    + "republicar el subgrafo primero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Base publicada y activa para la cuidadora"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "No se puede publicar: el cuerpo trae el motivo o los avisos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/{id}/publicar")
    public BaseReglaDTO publicar(@PathVariable Long id) {
        return servicio.publicar(id);
    }

    /** {@code POST /api/autoria/bases/{id}/descartar}: descarta los cambios sin publicar. */
    @Operation(summary = "Descarta los cambios sin publicar de la base de reglas",
            description = "Vuelve a la última versión publicada; la cuidadora ya veía esa versión.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador restaurado a lo publicado"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe", content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "No hay una versión publicada a la que volver",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/{id}/descartar")
    public BaseReglaDTO descartar(@PathVariable Long id) {
        return servicio.descartarCambios(id);
    }
}
