package org.signbb.backend.seguridad.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Registro de un padre o acudiente en la app de consulta.
 *
 * @param nombre     cómo quiere que lo llamen
 * @param correo     correo con el que va a ingresar
 * @param contrasena al menos 8 caracteres
 * @param dispositivoSeguro si es {@code true}, la sesión dura más tiempo. Si
 *                        falta, vale {@code false}
 */
@Schema(description = "Registro de un padre o acudiente")
public record RegistroRequest(String nombre, String correo, String contrasena,
        @Schema(description = "Dispositivo seguro: la sesión se mantiene abierta más tiempo. "
                + "Si falta, se toma como false.", example = "false")
        Boolean dispositivoSeguro) {

    /** Registro sin indicar si el dispositivo es seguro (se toma como no seguro). */
    public RegistroRequest(String nombre, String correo, String contrasena) {
        this(nombre, correo, contrasena, null);
    }

    /** True solo si el dispositivo se indicó como seguro. */
    public boolean esDispositivoSeguro() {
        return Boolean.TRUE.equals(dispositivoSeguro);
    }
}
