package org.signbb.backend.observaciones.modelo;

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
public enum TipoInteraccion {
    SI_NO,
    SELECCION_UNICA,
    SELECCION_MULTIPLE
}