package org.signbb.backend.media;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formatos de imagen aceptados (png, jpeg, webp y gif). El formato se reconoce
 * por los primeros bytes del archivo, no por el tipo ni el nombre que declara
 * quien lo sube.
 *
 * @param extension extensión con la que se guarda
 * @param tipo      tipo de contenido ({@code Content-Type})
 */
public record FormatoImagen(String extension, String tipo) {

    /** Máximo de imágenes por pregunta. */
    public static final int MAXIMO_POR_PREGUNTA = 3;

    private static final Pattern NOMBRE = Pattern
            .compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp|gif)");

    /** True si es un nombre como los que genera el servidor (uuid y extensión permitida). */
    public static boolean nombreValido(String nombre) {
        return nombre != null && NOMBRE.matcher(nombre).matches();
    }

    /** Tipo de contenido que corresponde a un nombre válido. */
    public static String tipoDe(String nombre) {
        if (nombre.endsWith(".png")) {
            return "image/png";
        }
        if (nombre.endsWith(".jpg")) {
            return "image/jpeg";
        }
        if (nombre.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/gif";
    }

    /** Reconoce el formato por la firma del archivo; vacío si no es una imagen permitida. */
    public static Optional<FormatoImagen> detectar(byte[] datos) {
        if (empieza(datos, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(new FormatoImagen("png", "image/png"));
        }
        if (empieza(datos, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(new FormatoImagen("jpg", "image/jpeg"));
        }
        if (empieza(datos, 'G', 'I', 'F', '8') && datos.length > 5 && (datos[4] == '7' || datos[4] == '9')
                && datos[5] == 'a') {
            return Optional.of(new FormatoImagen("gif", "image/gif"));
        }
        if (datos.length >= 12 && empieza(datos, 'R', 'I', 'F', 'F')
                && datos[8] == 'W' && datos[9] == 'E' && datos[10] == 'B' && datos[11] == 'P') {
            return Optional.of(new FormatoImagen("webp", "image/webp"));
        }
        return Optional.empty();
    }

    private static boolean empieza(byte[] datos, int... firma) {
        if (datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((datos[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
