package org.signbb.backend.observaciones.modelo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cómo se le presenta la pregunta al cuidador y cómo debe interpretarse su
 * respuesta.
 *
 * <p>
 * Hoy este dato no viene del grafo clínico (ni {@code Nodo} ni el JSON lo
 * distinguen) — {@code ServicioCaptura} lo infiere de la forma de la petición
 * (ver {@code ControladorConsulta}), o lo recibe como una pista opcional del
 * cliente. Es puramente informativo para la auditoría (sección 6 del
 * documento de diseño): la validación real solo distingue "una opción" de
 * "varias opciones".
 */
@Schema(description = "Cómo se presenta la pregunta y cómo se interpreta la respuesta.")
public enum TipoInteraccion {
    SI_NO,
    SELECCION_UNICA,
    SELECCION_MULTIPLE
}