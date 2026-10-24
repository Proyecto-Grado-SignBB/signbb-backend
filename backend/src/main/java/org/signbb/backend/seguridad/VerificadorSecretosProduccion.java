package org.signbb.backend.seguridad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Impide arrancar en producción con los valores de ejemplo del repositorio o
 * con la documentación de la API abierta.
 *
 * <p>El entorno se indica con {@code SIGNBB_ENTORNO} ({@code signbb.entorno}):
 * {@code desarrollo} (por defecto) o {@code produccion}. En producción, si el
 * secreto del JWT, la contraseña del supervisor inicial o las claves de Silo
 * siguen valiendo lo mismo que en desarrollo, o si Swagger y {@code /v3/api-docs}
 * siguen activos ({@code SIGNBB_DOCS_ACTIVAS} distinto de {@code false}), la
 * aplicación no arranca y el mensaje dice qué variables corregir (nunca muestra
 * los valores). Un entorno
 * con cualquier otro texto también impide arrancar, para que un error de
 * escritura no deje la revisión apagada sin avisar.</p>
 *
 * <p>La revisión ocurre al construir este componente, antes de que se siembre
 * el supervisor inicial o se atienda alguna petición.</p>
 */
@Component
public class VerificadorSecretosProduccion {

    /** Entorno por defecto: no se revisan los secretos. */
    public static final String ENTORNO_DESARROLLO = "desarrollo";

    /** Entorno que exige secretos propios. */
    public static final String ENTORNO_PRODUCCION = "produccion";

    /** Secreto JWT de ejemplo de {@code application.properties}. */
    public static final String JWT_DESARROLLO = "signbb-desarrollo-secreto-cambiar-en-produccion-2026";

    /** Contraseña de ejemplo del supervisor inicial. */
    public static final String CONTRASENA_SUPERVISOR_DESARROLLO = "Supervisor123";

    /** Usuario de ejemplo de Silo. */
    public static final String MEDIA_ACCESO_DESARROLLO = "silo-admin";

    /** Clave de ejemplo de Silo. */
    public static final String MEDIA_SECRETO_DESARROLLO = "ejemplo123";

    private static final Logger log = LoggerFactory.getLogger(VerificadorSecretosProduccion.class);

    /**
     * Revisa el entorno y, si es producción, los secretos.
     *
     * @throws IllegalStateException si el entorno no es válido o, en producción,
     *         algún secreto conserva su valor de desarrollo o la documentación
     *         de la API sigue activa
     */
    public VerificadorSecretosProduccion(
            @Value("${signbb.entorno:" + ENTORNO_DESARROLLO + "}") String entorno,
            @Value("${signbb.seguridad.jwt.secreto}") String jwtSecreto,
            @Value("${signbb.seguridad.supervisor-inicial.contrasena}") String contrasenaSupervisor,
            @Value("${signbb.media.access-key:" + MEDIA_ACCESO_DESARROLLO + "}") String mediaAcceso,
            @Value("${signbb.media.secret-key:" + MEDIA_SECRETO_DESARROLLO + "}") String mediaSecreto,
            @Value("${signbb.docs.activas:true}") boolean docsActivas) {
        String normalizado = entorno == null ? "" : entorno.trim().toLowerCase(Locale.ROOT);
        if (normalizado.equals(ENTORNO_DESARROLLO)) {
            log.info("Entorno de desarrollo: no se revisan los secretos de ejemplo.");
            return;
        }
        if (!normalizado.equals(ENTORNO_PRODUCCION) && !normalizado.equals("producción")) {
            throw new IllegalStateException("SIGNBB_ENTORNO debe ser '" + ENTORNO_DESARROLLO + "' o '"
                    + ENTORNO_PRODUCCION + "'; se recibió un valor distinto.");
        }
        List<String> pendientes = new ArrayList<>();
        if (JWT_DESARROLLO.equals(jwtSecreto)) {
            pendientes.add("SIGNBB_JWT_SECRETO");
        }
        if (CONTRASENA_SUPERVISOR_DESARROLLO.equals(contrasenaSupervisor)) {
            pendientes.add("SIGNBB_SUPERVISOR_CONTRASENA");
        }
        if (MEDIA_ACCESO_DESARROLLO.equals(mediaAcceso)) {
            pendientes.add("SIGNBB_MEDIA_ACCESS_KEY");
        }
        if (MEDIA_SECRETO_DESARROLLO.equals(mediaSecreto)) {
            pendientes.add("SIGNBB_MEDIA_SECRET_KEY");
        }
        if (docsActivas) {
            pendientes.add("SIGNBB_DOCS_ACTIVAS (debe ser false)");
        }
        if (!pendientes.isEmpty()) {
            throw new IllegalStateException("En producción no se pueden usar los valores de ejemplo ni dejar "
                    + "activa la documentación de la API. Revisa estas variables de entorno: "
                    + String.join(", ", pendientes));
        }
        log.info("Entorno de producción: los secretos no usan valores de ejemplo y la documentación está apagada.");
    }
}
