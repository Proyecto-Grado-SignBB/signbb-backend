package org.signbb.backend.observaciones.controladores;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

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
 * <li>{@code idPregunta} es opcional: el cliente indica qué pregunta está
 * contestando para que un clic repetido no responda la pregunta siguiente.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaRequest {
    @Schema(description = "Opción seleccionada (formato de una sola opción)")
    @Size(max = 200, message = "La respuesta no puede pasar de 200 caracteres.")
    private String respuesta;

    @Schema(description = "Todas las opciones marcadas (selección múltiple); deja respuesta nulo")
    @Size(max = 20, message = "No puedes marcar más de 20 opciones.")
    private List<@Size(max = 200, message = "Una opción no puede pasar de 200 caracteres.") String> opciones;

    @Schema(description = "Pista opcional para la auditoría: SI_NO, SELECCION_UNICA o SELECCION_MULTIPLE")
    @Size(max = 30, message = "El tipo de interacción no puede pasar de 30 caracteres.")
    private String tipo;

    @Schema(description = "Id de la pregunta que se está respondiendo (opcional). Si ya no es la actual "
            + "del recorrido, se responde 409 y no se registra nada")
    @Size(max = 100, message = "El id de la pregunta no puede pasar de 100 caracteres.")
    private String idPregunta;
}