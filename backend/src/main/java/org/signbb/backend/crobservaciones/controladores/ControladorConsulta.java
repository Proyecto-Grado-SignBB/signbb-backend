package org.signbb.backend.crobservaciones.controladores;

import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador REST de la consulta guiada del cuidador. */
@RestController
@RequestMapping("/api")
public class ControladorConsulta {

    /** Servicio de navegación (motor real: grafo + reglas). */
    @Autowired
    private ServicioNavegacion servicioNavegacion;

    /** {@code POST /api/recorridos}: inicia un recorrido y devuelve la primera pregunta. */
    @PostMapping("/recorridos")
    public ResponseEntity<RecorridoDTO> iniciar() {
        return ResponseEntity.ok(servicioNavegacion.iniciar());
    }

    /** {@code POST /api/recorridos/{id}/respuestas}: responde la pregunta actual y avanza. */
    @PostMapping("/recorridos/{recorridoId}/respuestas")
    public ResponseEntity<RecorridoDTO> responder(@PathVariable String recorridoId,
                                                  @RequestBody RespuestaRequest request) {
        return ResponseEntity.ok(servicioNavegacion.responder(recorridoId, request.getRespuesta()));
    }

    /** {@code GET /api/recorridos/{id}}: consulta el estado del recorrido. */
    @GetMapping("/recorridos/{recorridoId}")
    public ResponseEntity<RecorridoDTO> consultar(@PathVariable String recorridoId) {
        return ResponseEntity.ok(servicioNavegacion.consultar(recorridoId));
    }
}
