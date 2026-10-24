package org.signbb.backend.config;

/**
 * Expresiones regulares para las anotaciones {@code @Pattern} de los DTO de
 * entrada. {@code @Pattern} exige que coincida todo el texto, por eso no llevan
 * anclas.
 *
 * <p>
 * MODIFICADO (2.4): se agrega {@link #TELEFONO} para el número del pediatra.
 */
public final class PatronesTexto {

    /** Una sola línea: sin saltos de línea ni otros caracteres de control. */
    public static final String UNA_LINEA = "\\P{Cntrl}*";

    /** Texto largo: permite saltos de línea y tabulaciones, pero ningún otro carácter de control. */
    public static final String TEXTO_LARGO = "[\\P{Cntrl}\\n\\r\\t]*";

    /** Teléfono: dígitos, {@code +} y espacios, de 7 a 20 caracteres (por ejemplo, {@code +57 300 123 4567}). */
    public static final String TELEFONO = "[+0-9 ]{7,20}";

    private PatronesTexto() {
    }
}
