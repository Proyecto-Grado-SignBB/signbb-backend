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

    /**
     * Fragmento que detecta una etiqueta HTML/script ({@code <}, espacio opcional
     * y una letra, {@code !} o {@code /}): cubre {@code <script>}, {@code </script>},
     * {@code <img ...>}, {@code <!--...}, etc. Un {@code <} usado como comparación
     * ("T < 38°C") no cae aquí porque no lo sigue una letra, {@code !} ni {@code /}.
     */
    private static final String SIN_ETIQUETAS = "(?![\\s\\S]*<\\s*[a-zA-Z!/])";

    /** Una sola línea: sin saltos de línea, otros caracteres de control, ni etiquetas HTML/script. */
    public static final String UNA_LINEA = SIN_ETIQUETAS + "\\P{Cntrl}*";

    /**
     * Texto largo: permite saltos de línea y tabulaciones, pero ningún otro carácter
     * de control, ni etiquetas HTML/script (AS-03: rechazar código malicioso).
     */
    public static final String TEXTO_LARGO = SIN_ETIQUETAS + "[\\P{Cntrl}\\n\\r\\t]*";

    /** Teléfono: dígitos, {@code +} y espacios, de 7 a 20 caracteres (por ejemplo, {@code +57 300 123 4567}). */
    public static final String TELEFONO = "[+0-9 ]{7,20}";

    private PatronesTexto() {
    }
}
