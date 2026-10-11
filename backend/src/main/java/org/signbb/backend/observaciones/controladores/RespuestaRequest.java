package org.signbb.backend.observaciones.controladores;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de la petición que responde la pregunta actual.
 *
 * <p>
 * MODIFICADO respecto a la versión anterior: se agregan {@code opciones} y
 * {@code tipo}, ambos opcionales, para soportar selección múltiple sin romper
 * al cliente actual (que solo envía {@code respuesta}).
 *
 * <ul>
 * <li>Sí/No y selección única: igual que hoy, solo {@code respuesta}.</li>
 * <li>Selección múltiple: el cliente envía {@code opciones} con todas las
 * opciones marcadas (uno o más elementos) y deja {@code respuesta} nulo.</li>
 * <li>{@code tipo} es una pista opcional del frontend ("SI_NO",
 * "SELECCION_UNICA" o "SELECCION_MULTIPLE") para que la auditoría registre el
 * tipo exacto. Si no se envía, {@code ControladorConsulta} lo infiere del
 * tamaño de la lista resuelta — ver el javadoc de {@code responder}.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaRequest {
    @Schema(description = "Opción seleccionada (formato de una sola opción)")
    private String respuesta;

    @Schema(description = "Todas las opciones marcadas (selección múltiple); deja respuesta nulo")
    private List<String> opciones;

    @Schema(description = "Pista opcional para la auditoría: SI_NO, SELECCION_UNICA o SELECCION_MULTIPLE")
    private String tipo;
}