package org.signbb.backend.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuración del seed de demo: cuentas de ejemplo y ruta del manifiesto de
 * subgrafos. Todo se puede sobreescribir con variables de entorno.
 */
@Data
@Component
@ConfigurationProperties(prefix = "signbb.seed.demo")
public class SeedDemoProperties {

    /** Si es false, el runner del seed no se crea. */
    private boolean activa;

    /** Manifiesto de subgrafos dentro del classpath. */
    private String manifiesto = "seed/demo/seed.json";

    /** Cuenta del médico autor de los subgrafos. */
    private Cuenta medico = new Cuenta();

    /** Cuenta del padre de ejemplo. */
    private Cuenta padre = new Cuenta();

    /** Datos de una cuenta de ejemplo. */
    @Data
    public static class Cuenta {
        private String nombre;
        private String correo;
        private String contrasena;
        private String institucion;
    }
}
