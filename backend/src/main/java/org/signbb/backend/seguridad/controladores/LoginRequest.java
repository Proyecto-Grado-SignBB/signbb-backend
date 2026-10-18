package org.signbb.backend.seguridad.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ingreso con correo y contraseña.
 *
 * @param correo          correo de la cuenta
 * @param contrasena      contraseña de la cuenta
 * @param dispositivoSeguro si es {@code true}, un padre mantiene la sesión abierta
 *                        más tiempo; los médicos lo ignoran. Si falta, vale
 *                        {@code false}
 */
@Schema(description = "Ingreso con correo y contraseña")
public record LoginRequest(String correo, String contrasena,
        @Schema(description = "Dispositivo seguro: el padre mantiene la sesión abierta más tiempo "
                + "(los médicos lo ignoran). Si falta, se toma como false.", example = "false")
        Boolean dispositivoSeguro) {

    /** Ingreso sin indicar si el dispositivo es seguro (se toma como no seguro). */
    public LoginRequest(String correo, String contrasena) {
        this(correo, contrasena, null);
    }

    /** True solo si el dispositivo se indicó como seguro. */
    public boolean esDispositivoSeguro() {
        return Boolean.TRUE.equals(dispositivoSeguro);
    }
}
