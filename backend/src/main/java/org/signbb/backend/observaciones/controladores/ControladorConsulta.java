package org.signbb.backend.observaciones.controladores;

import java.util.List;

import org.signbb.backend.observaciones.modelo.TipoInteraccion;
import org.signbb.backend.observaciones.servicios.ServicioCaptura;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de la consulta guiada del cuidador.
 *
 * <p>
 * MODIFICADO: {@code responder} ahora pasa por {@link ServicioCaptura} en vez
 * de llamar a {@link ServicioNavegacion} directamente, para que toda
 * respuesta (una opción o varias) quede validada y auditada. {@code iniciar}
 * y {@code consultar} no cambian — no reciben una observación del cuidador.
 */
@RestController
@RequestMapping("/api")
public class ControladorConsulta {

    /** Servicio de navegación (motor real: grafo + reglas). */
    @Autowired
    private ServicioNavegacion servicioNavegacion;

    /** Servicio de captura: valida, audita y reenvía la respuesta del cuidador. */
    @Autowired
    private ServicioCaptura servicioCaptura;

    /**
     * {@code POST /api/recorridos}: inicia un recorrido y devuelve la primera
     * pregunta.
     */
    @PostMapping("/recorridos")
    public ResponseEntity<RecorridoDTO> iniciar() {
        return ResponseEntity.ok(servicioNavegacion.iniciar());
    }

    /**
     * {@code POST /api/recorridos/{id}/respuestas}: responde la pregunta actual
     * y avanza. Acepta tanto el formato existente ({@code respuesta}, una sola
     * opción) como selección múltiple ({@code opciones}, varias).
     */
    @PostMapping("/recorridos/{recorridoId}/respuestas")
    public ResponseEntity<RecorridoDTO> responder(@PathVariable String recorridoId,
            @RequestBody RespuestaRequest request) {
        List<String> seleccionadas = request.getOpciones() != null
                ? request.getOpciones()
                : List.of(request.getRespuesta());

        TipoInteraccion tipo = resolverTipo(request, seleccionadas);

        return ResponseEntity.ok(servicioCaptura.registrarRespuesta(recorridoId, tipo, seleccionadas));
    }

    /** {@code GET /api/recorridos/{id}}: consulta el estado del recorrido. */
    @GetMapping("/recorridos/{recorridoId}")
    public ResponseEntity<RecorridoDTO> consultar(@PathVariable String recorridoId) {
        return ResponseEntity.ok(servicioNavegacion.consultar(recorridoId));
    }

    /**
     * Resuelve el {@link TipoInteraccion} a registrar en la auditoría: usa la
     * pista {@code request.getTipo()} si el cliente la envía; si no, lo infiere
     * del tamaño de la lista (más de una opción solo puede ser selección
     * múltiple). Es un dato informativo — no afecta si el recorrido avanza o
     * no, solo lo que queda registrado. Ver sección 2 del documento de diseño:
     * hoy no hay forma de distinguir con certeza SI_NO de SELECCION_UNICA sin
     * este dato explícito del cliente.
     */
    private TipoInteraccion resolverTipo(RespuestaRequest request, List<String> seleccionadas) {
        if (request.getTipo() != null) {
            try {
                return TipoInteraccion.valueOf(request.getTipo().toUpperCase());
            } catch (IllegalArgumentException ignorada) {
                // pista inválida del cliente: cae al valor inferido de abajo
            }
        }
        return seleccionadas.size() > 1 ? TipoInteraccion.SELECCION_MULTIPLE : TipoInteraccion.SI_NO;
    }
}