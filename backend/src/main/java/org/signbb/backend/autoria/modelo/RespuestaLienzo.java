package org.signbb.backend.autoria.modelo;

/**
 * Respuesta de una pregunta del lienzo.
 *
 * @param id       id generado por el servidor ({@code r_N})
 * @param texto    texto que ve la cuidadora; al compilar es la
 *                 {@code relation} de la arista
 * @param gravedad semáforo de la respuesta; si llega nula se toma
 *                 {@link Gravedad#LEVE}
 * @param destino  id del nodo al que lleva, o {@code null} si todavía es un
 *                 "+" pendiente
 */
public record RespuestaLienzo(String id, String texto, Gravedad gravedad, String destino) {

    /** Normaliza la gravedad ausente a {@link Gravedad#LEVE}. */
    public RespuestaLienzo {
        gravedad = gravedad == null ? Gravedad.LEVE : gravedad;
    }

    /** Copia con otro destino. */
    public RespuestaLienzo conDestino(String nuevoDestino) {
        return new RespuestaLienzo(id, texto, gravedad, nuevoDestino);
    }
}
