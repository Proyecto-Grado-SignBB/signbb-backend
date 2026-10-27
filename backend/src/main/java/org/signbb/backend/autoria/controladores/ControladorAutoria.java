package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.servicios.GuardiaAutoria;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * API REST del lienzo de autoría. Las operaciones que editan devuelven el
 * borrador completo ({@link SubgrafoDTO}). Errores: 404 si el subgrafo, el
 * nodo o la respuesta no existen; 422 con {@link ErrorAutoriaDTO} si la
 * edición no es válida o el borrador no se puede publicar; 409 si otra
 * persona lo cambió al mismo tiempo. Lee el autor o un supervisor y edita
 * solo el autor ({@link GuardiaAutoria}); 403 si no. El supervisor autor
 * publica directamente con {@code POST /subgrafos/{id}/publicar}; el resto de
 * los médicos publica al aprobar su revisión ({@link ControladorRevision}).
 */
@RestController
@RequestMapping("/api/autoria")
@Tag(name = "Autoría", description = "Lienzo del médico para armar y validar "
        + "subgrafos clínicos. El supervisor autor los publica directamente; los demás médicos, "
        + "al aprobar su revisión. Solo el autor edita. Las operaciones que editan devuelven el borrador completo. "
        + "Errores: 404 si el recurso no existe; 422 con {mensaje, avisos} si la edición "
        + "no es válida o el borrador no se puede publicar; 409 si otra persona lo cambió.")
public class ControladorAutoria {

    private final ServicioAutoria servicio;
    private final GuardiaAutoria guardia;

    /** Inyecta el servicio de autoría y la guardia que revisa quién puede qué. */
    public ControladorAutoria(ServicioAutoria servicio, GuardiaAutoria guardia) {
        this.servicio = servicio;
        this.guardia = guardia;
    }

    /** {@code GET /api/autoria/categorias}: preguntas puente del grafo raíz. */
    @Operation(summary = "Categorías del grafo raíz",
            description = "Preguntas puente en las que se puede publicar un subgrafo.")
    @ApiResponse(responseCode = "200", description = "Lista de categorías")
    @GetMapping("/categorias")
    public List<CategoriaDTO> categorias() {
        return servicio.categorias();
    }

    /** {@code GET /api/autoria/subgrafos}: tablero de borradores. */
    @Operation(summary = "Lista los borradores de subgrafos",
            description = "Del último editado al primero.")
    @ApiResponse(responseCode = "200", description = "Resumen de cada borrador")
    @GetMapping("/subgrafos")
    public List<ResumenSubgrafoDTO> listar(UsuarioActual usuario) {
        return servicio.listar(usuario);
    }

    /** {@code POST /api/autoria/subgrafos}: crea un borrador con el lienzo vacío (201). */
    @Operation(summary = "Crea un borrador de subgrafo",
            description = "Crea el borrador con el lienzo vacío.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Borrador creado"),
            @ApiResponse(responseCode = "422", description = "Datos inválidos (por ejemplo, etiquetaOpcion vacía)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos")
    public ResponseEntity<SubgrafoDTO> crear(@Valid @RequestBody SubgrafoRequest datos, UsuarioActual usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos, usuario));
    }

    /** {@code GET /api/autoria/subgrafos/{id}}: borrador completo. */
    @Operation(summary = "Obtiene un borrador completo",
            description = "Incluye el lienzo y los avisos calculados en el momento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador completo"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content)
    })
    @GetMapping("/subgrafos/{id}")
    public SubgrafoDTO obtener(@PathVariable Long id, UsuarioActual usuario) {
        guardia.leerSubgrafo(id, usuario);
        return servicio.obtener(id);
    }

    /** {@code PUT /api/autoria/subgrafos/{id}}: cambia los datos del subgrafo. */
    @Operation(summary = "Actualiza los datos del subgrafo",
            description = "Cambia nombre, categoría o texto de la opción. Solo el autor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "Datos inválidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PutMapping("/subgrafos/{id}")
    public SubgrafoDTO actualizar(@PathVariable Long id, @Valid @RequestBody SubgrafoRequest datos,
            UsuarioActual usuario) {
        guardia.editarSubgrafo(id, usuario);
        return servicio.actualizar(id, datos);
    }

    /** {@code DELETE /api/autoria/subgrafos/{id}}: borra un borrador nunca publicado (204). */
    @Operation(summary = "Elimina un borrador",
            description = "Solo si nunca se publicó.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrador eliminado"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @DeleteMapping("/subgrafos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, UsuarioActual usuario) {
        guardia.editarSubgrafo(id, usuario);
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code POST /api/autoria/subgrafos/{id}/nodos}: el "+" del lienzo. */
    @Operation(summary = "Agrega un nodo (el \"+\")",
            description = "Crea la primera pregunta o un nodo después de una respuesta sin camino.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado con el nodo nuevo"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "La edición no es válida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/nodos")
    public SubgrafoDTO agregarNodo(@PathVariable Long id, @Valid @RequestBody NodoRequest nodo, UsuarioActual usuario) {
        guardia.editarSubgrafo(id, usuario);
        return servicio.agregarNodo(id, nodo);
    }

    /** {@code PUT /api/autoria/subgrafos/{id}/nodos/{nodoId}}: edita un nodo. */
    @Operation(summary = "Edita un nodo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador o el nodo no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "La edición no es válida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PutMapping("/subgrafos/{id}/nodos/{nodoId}")
    public SubgrafoDTO editarNodo(@PathVariable Long id, @PathVariable String nodoId,
            @Valid @RequestBody NodoRequest nodo, UsuarioActual usuario) {
        guardia.editarSubgrafo(id, usuario);
        return servicio.editarNodo(id, nodoId, nodo);
    }

    /** {@code DELETE /api/autoria/subgrafos/{id}/nodos/{nodoId}}: elimina un nodo y su rama. */
    @Operation(summary = "Elimina un nodo y su rama",
            description = "Las respuestas que llegaban vuelven a quedar como \"+\". Si alguna regla del "
                    + "subgrafo usa el nodo (o su rama) como condición o destino, se agrega un aviso "
                    + "bloqueante que impide publicar hasta corregir la regla.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador o el nodo no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @DeleteMapping("/subgrafos/{id}/nodos/{nodoId}")
    public SubgrafoDTO eliminarNodo(@PathVariable Long id, @PathVariable String nodoId, UsuarioActual usuario) {
        guardia.editarSubgrafo(id, usuario);
        return servicio.eliminarNodo(id, nodoId);
    }

    /**
     * {@code PUT /api/autoria/subgrafos/{id}/respuestas/{respuestaId}/destino}:
     * la opción "Pregunta existente".
     */
    @Operation(summary = "Enlaza una respuesta a una pregunta existente",
            description = "La opción \"Pregunta existente\". Enviar nodoId nulo quita el enlace.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador, la respuesta o el nodo destino no existe",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "La edición no es válida (por ejemplo, crearía un círculo)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PutMapping("/subgrafos/{id}/respuestas/{respuestaId}/destino")
    public SubgrafoDTO enlazar(@PathVariable Long id, @PathVariable String respuestaId,
            @Valid @RequestBody DestinoRequest destino, UsuarioActual usuario) {
        guardia.editarSubgrafo(id, usuario);
        return servicio.enlazar(id, respuestaId, destino == null ? null : destino.getNodoId());
    }

    /** {@code GET /api/autoria/subgrafos/{id}/avisos}: avisos del lienzo. */
    @Operation(summary = "Avisos del lienzo",
            description = "Algunos bloquean la publicación y otros solo advierten.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avisos actuales"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content)
    })
    @GetMapping("/subgrafos/{id}/avisos")
    public List<Aviso> avisos(@PathVariable Long id, UsuarioActual usuario) {
        guardia.leerSubgrafo(id, usuario);
        return servicio.avisos(id);
    }

    /**
     * {@code POST /api/autoria/subgrafos/{id}/publicar}: el supervisor autor
     * publica el subgrafo sin pasar por revisión.
     */
    @Operation(summary = "Publica directamente el subgrafo",
            description = "Solo el supervisor autor. Publica el subgrafo y su base de reglas en una sola "
                    + "acción, sin enviarlo a revisión. Se permite desde BORRADOR o CAMBIOS_SIN_PUBLICAR, "
                    + "y también si ya está publicado pero sus reglas tienen cambios sin publicar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo publicado"),
            @ApiResponse(responseCode = "403", description = "No es supervisor o no es el autor",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "El subgrafo no existe",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "No está en un estado publicable o el contenido "
                    + "tiene avisos bloqueantes",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/publicar")
    public SubgrafoDTO publicar(@PathVariable Long id, UsuarioActual usuario) {
        guardia.publicarSubgrafo(id, usuario);
        return servicio.publicar(id);
    }

    /** {@code POST /api/autoria/subgrafos/{id}/despublicar}: retira el subgrafo y su base de reglas de la app. */
    @Operation(summary = "Despublica el subgrafo",
            description = "Solo supervisor. Las cuidadoras que empiecen un recorrido nuevo ya no lo ven. Las que ya estaban "
                    + "en medio de un recorrido lo terminan. Se retira también su base de reglas; el borrador "
                    + "del lienzo y el de reglas vuelven a BORRADOR con su documento intacto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo despublicado"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "No está publicado o el contenido restante no es válido",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/despublicar")
    public SubgrafoDTO despublicar(@PathVariable Long id) {
        return servicio.despublicar(id);
    }

    /**
     * {@code POST /api/autoria/subgrafos/{id}/descartar}: descarta los cambios
     * sin publicar y devuelve el borrador a su última publicación.
     */
    @PostMapping("/subgrafos/{id}/descartar")
    public SubgrafoDTO descartar(@PathVariable Long id, UsuarioActual usuario) {
        guardia.editarSubgrafo(id, usuario);
        return servicio.descartarCambios(id);
    }
}
