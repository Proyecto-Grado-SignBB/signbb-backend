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

/**
 * API REST del lienzo de autoría. Las operaciones que editan devuelven el
 * borrador completo ({@link SubgrafoDTO}). Errores: 404 si el subgrafo, el
 * nodo o la respuesta no existen; 422 con {@link ErrorAutoriaDTO} si la
 * edición no es válida o el borrador no se puede publicar; 409 si otra
 * persona lo cambió al mismo tiempo.
 */
@RestController
@RequestMapping("/api/autoria")
public class ControladorAutoria {

    private final ServicioAutoria servicio;

    /** Inyecta el servicio de autoría. */
    public ControladorAutoria(ServicioAutoria servicio) {
        this.servicio = servicio;
    }

    /** {@code GET /api/autoria/categorias}: preguntas puente del grafo raíz. */
    @GetMapping("/categorias")
    public List<CategoriaDTO> categorias() {
        return servicio.categorias();
    }

    /** {@code GET /api/autoria/subgrafos}: tablero de borradores. */
    @GetMapping("/subgrafos")
    public List<ResumenSubgrafoDTO> listar() {
        return servicio.listar();
    }

    /** {@code POST /api/autoria/subgrafos}: crea un borrador con el lienzo vacío (201). */
    @PostMapping("/subgrafos")
    public ResponseEntity<SubgrafoDTO> crear(@RequestBody SubgrafoRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
    }

    /** {@code GET /api/autoria/subgrafos/{id}}: borrador completo. */
    @GetMapping("/subgrafos/{id}")
    public SubgrafoDTO obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** {@code PUT /api/autoria/subgrafos/{id}}: cambia los datos del subgrafo. */
    @PutMapping("/subgrafos/{id}")
    public SubgrafoDTO actualizar(@PathVariable Long id, @RequestBody SubgrafoRequest datos) {
        return servicio.actualizar(id, datos);
    }

    /** {@code DELETE /api/autoria/subgrafos/{id}}: borra un borrador nunca publicado (204). */
    @DeleteMapping("/subgrafos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code POST /api/autoria/subgrafos/{id}/nodos}: el "+" del lienzo. */
    @PostMapping("/subgrafos/{id}/nodos")
    public SubgrafoDTO agregarNodo(@PathVariable Long id, @RequestBody NodoRequest nodo) {
        return servicio.agregarNodo(id, nodo);
    }

    /** {@code PUT /api/autoria/subgrafos/{id}/nodos/{nodoId}}: edita un nodo. */
    @PutMapping("/subgrafos/{id}/nodos/{nodoId}")
    public SubgrafoDTO editarNodo(@PathVariable Long id, @PathVariable String nodoId,
            @RequestBody NodoRequest nodo) {
        return servicio.editarNodo(id, nodoId, nodo);
    }

    /** {@code DELETE /api/autoria/subgrafos/{id}/nodos/{nodoId}}: elimina un nodo y su rama. */
    @DeleteMapping("/subgrafos/{id}/nodos/{nodoId}")
    public SubgrafoDTO eliminarNodo(@PathVariable Long id, @PathVariable String nodoId) {
        return servicio.eliminarNodo(id, nodoId);
    }

    /**
     * {@code PUT /api/autoria/subgrafos/{id}/respuestas/{respuestaId}/destino}:
     * la opción "Pregunta existente".
     */
    @PutMapping("/subgrafos/{id}/respuestas/{respuestaId}/destino")
    public SubgrafoDTO enlazar(@PathVariable Long id, @PathVariable String respuestaId,
            @RequestBody DestinoRequest destino) {
        return servicio.enlazar(id, respuestaId, destino == null ? null : destino.getNodoId());
    }

    /** {@code GET /api/autoria/subgrafos/{id}/avisos}: avisos del lienzo. */
    @GetMapping("/subgrafos/{id}/avisos")
    public List<Aviso> avisos(@PathVariable Long id) {
        return servicio.avisos(id);
    }

    /** {@code POST /api/autoria/subgrafos/{id}/publicar}: publica el borrador. */
    @PostMapping("/subgrafos/{id}/publicar")
    public SubgrafoDTO publicar(@PathVariable Long id) {
        return servicio.publicar(id);
    }
}
