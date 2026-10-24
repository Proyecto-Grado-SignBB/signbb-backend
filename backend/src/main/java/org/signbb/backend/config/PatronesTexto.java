package org.signbb.backend.config;

/**
 * Expresiones regulares para las anotaciones {@code @Pattern} de los DTO de
 * entrada. {@code @Pattern} exige que coincida todo el texto, por eso no llevan
 * anclas.
 */
public final class PatronesTexto {

    /** Una sola línea: sin saltos de línea ni otros caracteres de control. */
    public static final String UNA_LINEA = "\\P{Cntrl}*";

    /** Texto largo: permite saltos de línea y tabulaciones, pero ningún otro carácter de control. */
    public static final String TEXTO_LARGO = "[\\P{Cntrl}\\n\\r\\t]*";

    private PatronesTexto() {
    }
}
