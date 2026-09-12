package org.signbb.backend.crobservaciones.controladores;

import org.signbb.backend.navegacion.servicios.Recorrido;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador REST de la consulta guiada del cuidador (en construcción). */
@RestController
@RequestMapping("/api")
public class ControladorConsulta {

    /** Servicio de navegación. */
    @Autowired
    private ServicioNavegacion servicioNavegacion;

    /** {@code POST /api/recorridos}: inicia un recorrido. */
    @PostMapping("/recorridos")
    public ResponseEntity<Recorrido> iniciar(@RequestBody IniciarRecorridoRequest request) {
        Recorrido recorrido = servicioNavegacion.iniciar(request.getGrafoClinicoId());
        return ResponseEntity.ok(recorrido);
    }

    /** {@code POST /api/recorridos/{id}/respuestas}: responde y avanza. */
    @PostMapping("/recorridos/{recorridoId}/respuestas")
    public ResponseEntity<Recorrido> responder(@PathVariable long recorridoId,
                                                @RequestBody RespuestaRequest request) {
        Recorrido recorrido = servicioNavegacion.responder(recorridoId, request.getRespuesta());
        return ResponseEntity.ok(recorrido);
    }

    /** {@code GET /api/recorridos/{id}}: consulta el recorrido. */
    @GetMapping("/recorridos/{recorridoId}")
    public ResponseEntity<Recorrido> consultar(@PathVariable long recorridoId) {
        Recorrido recorrido = servicioNavegacion.consultar(recorridoId);
        return ResponseEntity.ok(recorrido);
    }
}
