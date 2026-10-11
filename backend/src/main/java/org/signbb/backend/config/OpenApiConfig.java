package org.signbb.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
 */
@Configuration
public class OpenApiConfig {

    /** Información de la API mostrada por Swagger UI. */
    @Bean
    public OpenAPI signbbOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SignBB API")
                        .version("0.0.1")
                        .description("API REST del sistema de apoyo al triaje clínico SignBB: "
                                + "consulta guiada del cuidador (recorridos) y autoría de "
                                + "subgrafos clínicos. Los endpoints de edición de reglas "
                                + "se documentarán en este mismo contrato cuando estén disponibles."))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Servidor local (perfil docker)")));
    }
}
