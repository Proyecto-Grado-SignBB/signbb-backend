package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.TipoNodo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.signbb.backend.config.PatronesTexto;

/**
 * Nodo que manda el panel del lienzo, para crearlo (el "+") o editarlo.
 *
 * <ul>
 * <li>{@code despuesDe}: al crear, la respuesta cuyo "+" se tocó; {@code null}
 * para la primera pregunta. Al editar se ignora.</li>
 * <li>{@code tipo}: obligatorio al crear; al editar puede omitirse.</li>
 * <li>{@code desenlace}: solo conclusiones.</li>
 * <li>{@code respuestas}: solo preguntas; al editar, las que traen
 * {@code id} conservan su destino.</li>
 * <li>{@code imagenes}: solo preguntas; nombres devueltos al subir cada
 * imagen (máximo 3). Al editar, si se omite se conservan las actuales.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodoRequest {
    @Schema(description = "Al crear, la respuesta cuyo \"+\" se tocó; nulo para la primera pregunta. Al editar se ignora.")
    @Valid
    private DespuesDeRequest despuesDe;

    @Schema(description = "Tipo de nodo; obligatorio al crear, puede omitirse al editar")
    private TipoNodo tipo;

    @Schema(description = "Texto de la pregunta o conclusión")
    @Size(max = 1000, message = "El texto no puede pasar de 1000 caracteres.")
    @Pattern(regexp = PatronesTexto.TEXTO_LARGO, message = "El texto tiene caracteres que no se aceptan.")
    private String texto;

    @Schema(description = "Si la pregunta admite selección múltiple")
    private Boolean variasRespuestas;

    @Schema(description = "Desenlace; solo para conclusiones")
    private Desenlace desenlace;

    @Schema(description = "Respuestas; solo para preguntas. Al editar, las que traen id conservan su destino")
    @Size(max = 10, message = "Una pregunta no puede tener más de 10 respuestas.")
    private List<@Valid OpcionRequest> respuestas;
    private List<String> imagenes;

    /** Nodo sin imágenes. */
    public NodoRequest(DespuesDeRequest despuesDe, TipoNodo tipo, String texto, Boolean variasRespuestas,
            Desenlace desenlace, List<OpcionRequest> respuestas) {
        this(despuesDe, tipo, texto, variasRespuestas, desenlace, respuestas, null);
    }
}
