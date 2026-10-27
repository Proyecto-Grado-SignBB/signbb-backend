package org.signbb.backend.seed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el seed de demo al arrancar, después de la seguridad y del grafo
 * raíz. Solo existe si {@code signbb.seed.demo.activa=true}.
 */
@Component
@Order(30)
@ConditionalOnProperty(name = "signbb.seed.demo.activa", havingValue = "true")
public class SeedDemoRunner implements ApplicationRunner {

    private final SeedDemoService servicio;
    private final String entorno;

    /** Inyecta el servicio del seed y el entorno de ejecución. */
    public SeedDemoRunner(SeedDemoService servicio,
            @Value("${signbb.entorno:desarrollo}") String entorno) {
        this.servicio = servicio;
        this.entorno = entorno;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("produccion".equalsIgnoreCase(entorno)) {
            throw new IllegalStateException(
                    "El seed de demo no se puede activar con signbb.entorno=produccion.");
        }
        servicio.sembrar();
    }
}
