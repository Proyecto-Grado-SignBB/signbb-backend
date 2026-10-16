package org.signbb.backend.seguridad.controladores;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Activa o desactiva una cuenta.
 *
 * @param activo {@code false} impide ingresar e invalida sus tokens
 */
@Schema(description = "Activa o desactiva una cuenta")
public record ActivoRequest(Boolean activo) {
}
