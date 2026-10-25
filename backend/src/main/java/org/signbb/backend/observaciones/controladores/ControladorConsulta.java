package org.signbb.backend.observaciones.controladores;

import java.util.List;

import org.signbb.backend.observaciones.modelo.TipoInteraccion;
import org.signbb.backend.autoria.controladores.ErrorAutoriaDTO;
import org.signbb.backend.observaciones.servicios.ServicioCaptura;
import org.signbb.backend.observaciones.servicios.ServicioRecorridos;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
import jakarta.validation.Valid;

/**
 * Controlador REST de la consulta guiada del cuidador.
 *
 * <p>
 * MODIFICADO: {@code responder} ahora pasa por {@link ServicioCaptura} en vez
 * de llamar a {@link ServicioNavegacion} directamente, para que toda
 * respuesta (una opción o varias) quede validada y auditada. {@code iniciar}
 * y {@code consultar} no cambian — no reciben una observación del cuidador.
 * {@code responder} y {@code retroceder} van con candado por recorrido y
 * guardan Redis y Postgres como "todo o nada" (ver {@link ServicioCaptura}).
 *
 * <p>
 * Cada recorrido es del padre que lo inició ({@link ServicioRecorridos}): antes
 * de responder, retroceder o consultar se revisa que sea suyo y, si no, se
 * responde 404 para no revelar que existe.
 *
 * <p>
 * MODIFICADO (CU10/CU11): consultar, responder y retroceder revisan además que
 * la consulta siga vigente; si venció tras 8 horas sin actividad o ya se
 * reinició, responden 410. Se agrega {@code reiniciar}.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Consulta", description = "Recorrido guiado del cuidador: inicia el triaje, "
        + "responde cada pregunta y consulta el estado. Toda respuesta queda auditada.")
public class ControladorConsulta {

    /** Servicio de navegación (motor real: grafo + reglas). */
    @Autowired
    private ServicioNavegacion servicioNavegacion;

    /** Servicio de captura: valida, audita y reenvía la respuesta del cuidador. */
    @Autowired
    private ServicioCaptura servicioCaptura;

    /** Dueño y estado durable de cada recorrido. */
    @Autowired
    private ServicioRecorridos servicioRecorridos;

    /**
     * {@code POST /api/recorridos}: inicia un recorrido y devuelve la primera
     * pregunta.
     */
    @Operation(summary = "Inicia un recorrido de triaje",
            description = "Crea una sesión y devuelve la primera pregunta. El id del "
                    + "recorrido se envía en el cuerpo y se usa en las operaciones siguientes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recorrido creado con su primera pregunta"),
            @ApiResponse(responseCode = "503", description = "Caché de sesiones (Redis) no disponible",
                    content = @Content)
    })
    @PostMapping("/recorridos")
    public ResponseEntity<RecorridoDTO> iniciar(UsuarioActual usuario) {
        return ResponseEntity.ok(servicioRecorridos.iniciar(usuario));
    }

    /** {@code GET /api/recorridos}: "Mis recorridos" del padre. */
    @Operation(summary = "Lista los recorridos del padre",
            description = "Del más nuevo al más viejo, con su estado y, si terminó, su resultado.")
    @ApiResponse(responseCode = "200", description = "Recorridos del padre")
    @GetMapping("/recorridos")
    public ResponseEntity<List<ResumenRecorridoDTO>> misRecorridos(UsuarioActual usuario) {
        return ResponseEntity.ok(servicioRecorridos.deUsuario(usuario));
    }

    /**
     * {@code POST /api/recorridos/{id}/respuestas}: responde la pregunta actual
     * y avanza. Acepta tanto el formato existente ({@code respuesta}, una sola
     * opción) como selección múltiple ({@code opciones}, varias).
     */
    @Operation(summary = "Responde la pregunta actual",
            description = "Acepta tanto el formato de una sola opción (respuesta) como la "
                    + "selección múltiple (opciones); tipo es una pista opcional del cliente "
                    + "para la auditoría. Devuelve el recorrido actualizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta registrada y recorrido actualizado"),
            @ApiResponse(responseCode = "400", description = "La opción enviada no es válida para la pregunta actual",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "El recorrido no existe o es de otro padre",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra respuesta del mismo recorrido se está guardando, "
                            + "o la pregunta enviada (idPregunta) ya no es la actual",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "La consulta venció tras 8 horas sin actividad "
                    + "o se reinició",
                    content = @Content(schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "503", description = "Caché de sesiones (Redis) no disponible",
                    content = @Content)
    })
    @PostMapping("/recorridos/{recorridoId}/respuestas")
    public ResponseEntity<RecorridoDTO> responder(@PathVariable String recorridoId,
            @Valid @RequestBody RespuestaRequest request, UsuarioActual usuario) {
        servicioRecorridos.exigirDueno(recorridoId, usuario);
        servicioRecorridos.exigirVigente(recorridoId);
        List<String> seleccionadas = request.getOpciones() != null
                ? request.getOpciones()
                : request.getRespuesta() != null ? List.of(request.getRespuesta()) : List.of();
        if (seleccionadas.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una respuesta.");
        }

        TipoInteraccion tipo = resolverTipo(request, seleccionadas);

        return ResponseEntity.ok(servicioCaptura.registrarRespuesta(
                recorridoId, tipo, seleccionadas, usuario.id(), request.getIdPregunta()));
    }

    /**
     * {@code POST /api/recorridos/{id}/retroceder}: vuelve a la pregunta
     * anterior para cambiar su respuesta (400 si no hay pasos en el historial).
     */
    @Operation(summary = "Retrocede a la pregunta anterior",
            description = "Vuelve atrás para cambiar la última respuesta. Falla con 400 si "
                    + "el recorrido no tiene pasos en el historial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recorrido actualizado en la pregunta anterior"),
            @ApiResponse(responseCode = "400", description = "El recorrido no tiene historial que deshacer",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "El recorrido no existe o es de otro padre",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Otra operación del mismo recorrido se está guardando",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "La consulta venció tras 8 horas sin actividad "
                    + "o se reinició",
                    content = @Content(schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "503", description = "Caché de sesiones (Redis) no disponible",
                    content = @Content)
    })
    @PostMapping("/recorridos/{recorridoId}/retroceder")
    public ResponseEntity<RecorridoDTO> retroceder(@PathVariable String recorridoId, UsuarioActual usuario) {
        servicioRecorridos.exigirDueno(recorridoId, usuario);
        servicioRecorridos.exigirVigente(recorridoId);
        return ResponseEntity.ok(servicioCaptura.retroceder(recorridoId));
    }

    /**
     * {@code POST /api/recorridos/{id}/reiniciar}: deja la consulta como
     * reiniciada y empieza una nueva desde la primera pregunta (CU11).
     */
    @Operation(summary = "Reinicia la consulta",
            description = "Marca el recorrido como reiniciado (ABANDONADO), borra su sesión y "
                    + "devuelve un recorrido nuevo con su primera pregunta. Un recorrido terminado "
                    + "no se reinicia: su resultado queda en Mis recorridos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recorrido nuevo con su primera pregunta"),
            @ApiResponse(responseCode = "404", description = "El recorrido no existe o es de otro padre",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "El recorrido ya terminó, u otra operación del "
                    + "mismo recorrido se está guardando",
                    content = @Content(schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "410", description = "El recorrido ya se había reiniciado",
                    content = @Content(schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "503", description = "Caché de sesiones (Redis) no disponible",
                    content = @Content)
    })
    @PostMapping("/recorridos/{recorridoId}/reiniciar")
    public ResponseEntity<RecorridoDTO> reiniciar(@PathVariable String recorridoId, UsuarioActual usuario) {
        servicioRecorridos.exigirDueno(recorridoId, usuario);
        return ResponseEntity.ok(servicioRecorridos.reiniciar(recorridoId, usuario));
    }

    /** {@code GET /api/recorridos/{id}}: consulta el estado del recorrido. */
    @Operation(summary = "Consulta el estado del recorrido",
            description = "Devuelve la pregunta actual si sigue en curso, o el resultado "
                    + "final si ya terminó.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actual del recorrido"),
            @ApiResponse(responseCode = "404", description = "El recorrido no existe o es de otro padre",
                    content = @Content),
            @ApiResponse(responseCode = "410", description = "La consulta venció tras 8 horas sin actividad "
                    + "o se reinició",
                    content = @Content(schema = @Schema(implementation = ErrorAutoriaDTO.class))),
            @ApiResponse(responseCode = "503", description = "Caché de sesiones (Redis) no disponible",
                    content = @Content)
    })
    @GetMapping("/recorridos/{recorridoId}")
    public ResponseEntity<RecorridoDTO> consultar(@PathVariable String recorridoId, UsuarioActual usuario) {
        servicioRecorridos.exigirDueno(recorridoId, usuario);
        servicioRecorridos.exigirVigente(recorridoId);
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