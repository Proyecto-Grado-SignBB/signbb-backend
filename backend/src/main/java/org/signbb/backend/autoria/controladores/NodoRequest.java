package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.TipoNodo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private DespuesDeRequest despuesDe;
    private TipoNodo tipo;
    private String texto;
    private Boolean variasRespuestas;
    private Desenlace desenlace;
    private List<OpcionRequest> respuestas;
    private List<String> imagenes;

    /** Nodo sin imágenes. */
    public NodoRequest(DespuesDeRequest despuesDe, TipoNodo tipo, String texto, Boolean variasRespuestas,
            Desenlace desenlace, List<OpcionRequest> respuestas) {
        this(despuesDe, tipo, texto, variasRespuestas, desenlace, respuestas, null);
    }
}
