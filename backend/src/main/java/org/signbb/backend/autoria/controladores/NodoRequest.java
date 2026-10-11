package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.TipoNodo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodoRequest {
    @Schema(description = "Al crear, la respuesta cuyo \"+\" se tocó; nulo para la primera pregunta. Al editar se ignora.")
    private DespuesDeRequest despuesDe;

    @Schema(description = "Tipo de nodo; obligatorio al crear, puede omitirse al editar")
    private TipoNodo tipo;

    @Schema(description = "Texto de la pregunta o conclusión")
    private String texto;

    @Schema(description = "Si la pregunta admite selección múltiple")
    private Boolean variasRespuestas;

    @Schema(description = "Desenlace; solo para conclusiones")
    private Desenlace desenlace;

    @Schema(description = "Respuestas; solo para preguntas. Al editar, las que traen id conservan su destino")
    private List<OpcionRequest> respuestas;
}
