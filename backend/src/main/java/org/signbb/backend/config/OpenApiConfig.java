package org.signbb.backend.config;

import java.util.List;

import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Metadatos del contrato OpenAPI de SignBB. Los endpoints los descubre
 * springdoc a partir de los controladores; aquí solo se fija la información
 * general que aparece en la cabecera de Swagger UI.
 *
 * <p>
 * El contrato se publica en {@code /v3/api-docs} (JSON) y
 * {@code /v3/api-docs.yaml} (YAML), y la interfaz en
 * {@code /swagger-ui.html}. La copia versionada vive en
 * {@code docs/api/openapi.yaml}.
 *
 * <p>
 * Todas las operaciones piden el esquema {@value #ESQUEMA} (JWT en
 * {@code Authorization: Bearer}), salvo las que lo anulan con
 * {@code @SecurityRequirements} (registro e ingreso). El parámetro
 * {@link UsuarioActual} de los controladores no es parte del contrato: lo
 * llena el servidor desde el token.
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad en el contrato (botón «Authorize»). */
    public static final String ESQUEMA = "bearerAuth";

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UsuarioActual.class);
    }

    /** Información de la API mostrada por Swagger UI. */
    @Bean
    public OpenAPI signbbOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SignBB API")
                        .version("0.0.1")
                        .description("API REST del sistema de apoyo al triaje clínico SignBB: "
                                + "consulta guiada del padre o acudiente (recorridos), autoría de "
                                + "subgrafos clínicos y reglas, revisión del supervisor y cuentas. "
                                + "Ingresa en POST /api/auth/login y usa el token en «Authorize»."))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Servidor local (perfil docker)")));
    }
}
