package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
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

/**
 * API REST del lienzo de autoría. Las operaciones que editan devuelven el
 * borrador completo ({@link SubgrafoDTO}). Errores: 404 si el subgrafo, el
 * nodo o la respuesta no existen; 422 con {@link ErrorAutoriaDTO} si la
 * edición no es válida o el borrador no se puede publicar; 409 si otra
 * persona lo cambió al mismo tiempo.
 */
@RestController
@RequestMapping("/api/autoria")
@Tag(name = "Autoría", description = "Lienzo del médico para armar, validar y publicar "
        + "subgrafos clínicos. Las operaciones que editan devuelven el borrador completo. "
        + "Errores: 404 si el recurso no existe; 422 con {mensaje, avisos} si la edición "
        + "no es válida o el borrador no se puede publicar; 409 si otra persona lo cambió.")
public class ControladorAutoria {

    private final ServicioAutoria servicio;

    /** Inyecta el servicio de autoría. */
    public ControladorAutoria(ServicioAutoria servicio) {
        this.servicio = servicio;
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
    public List<ResumenSubgrafoDTO> listar() {
        return servicio.listar();
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
    public ResponseEntity<SubgrafoDTO> crear(@RequestBody SubgrafoRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
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
    public SubgrafoDTO obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** {@code PUT /api/autoria/subgrafos/{id}}: cambia los datos del subgrafo. */
    @Operation(summary = "Actualiza los datos del subgrafo",
            description = "Cambia nombre, categoría, texto de la opción, autor o institución.")
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
    public SubgrafoDTO actualizar(@PathVariable Long id, @RequestBody SubgrafoRequest datos) {
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
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
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
    public SubgrafoDTO agregarNodo(@PathVariable Long id, @RequestBody NodoRequest nodo) {
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
            @RequestBody NodoRequest nodo) {
        return servicio.editarNodo(id, nodoId, nodo);
    }

    /** {@code DELETE /api/autoria/subgrafos/{id}/nodos/{nodoId}}: elimina un nodo y su rama. */
    @Operation(summary = "Elimina un nodo y su rama",
            description = "Las respuestas que llegaban vuelven a quedar como \"+\".")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Borrador actualizado"),
            @ApiResponse(responseCode = "404", description = "El borrador o el nodo no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @DeleteMapping("/subgrafos/{id}/nodos/{nodoId}")
    public SubgrafoDTO eliminarNodo(@PathVariable Long id, @PathVariable String nodoId) {
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
            @RequestBody DestinoRequest destino) {
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
    public List<Aviso> avisos(@PathVariable Long id) {
        return servicio.avisos(id);
    }

    /** {@code POST /api/autoria/subgrafos/{id}/publicar}: publica el borrador. */
    @Operation(summary = "Publica el borrador",
            description = "Si hay avisos bloqueantes responde 422 con ellos en el cuerpo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo publicado y navegable por la cuidadora"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "No se puede publicar: el cuerpo trae los avisos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class)))
    })
    @PostMapping("/subgrafos/{id}/publicar")
    public SubgrafoDTO publicar(@PathVariable Long id) {
        return servicio.publicar(id);
    }

    /** {@code POST /api/autoria/subgrafos/{id}/despublicar}: retira el subgrafo y su base de reglas de la app. */
    @Operation(summary = "Despublica el subgrafo",
            description = "Las cuidadoras que empiecen un recorrido nuevo ya no lo ven. Las que ya estaban "
                    + "en medio de un recorrido lo terminan. Se retira también su base de reglas; el borrador "
                    + "del lienzo y el de reglas vuelven a BORRADOR con su documento intacto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subgrafo despublicado"),
            @ApiResponse(responseCode = "404", description = "El borrador no existe",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra persona lo cambió al mismo tiempo",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "422", description = "No está publicado, o era el único subgrafo publicado",
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
    public SubgrafoDTO descartar(@PathVariable Long id) {
        return servicio.descartarCambios(id);
    }
}
