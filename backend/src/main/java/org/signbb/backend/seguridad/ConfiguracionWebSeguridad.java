package org.signbb.backend.seguridad;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra {@link ResolvedorUsuarioActual} en Spring MVC. */
@Configuration
public class ConfiguracionWebSeguridad implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvedores) {
        resolvedores.add(new ResolvedorUsuarioActual());
    }
}
