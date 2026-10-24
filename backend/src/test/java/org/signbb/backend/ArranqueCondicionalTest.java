package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.servicios.ServicioReglas;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests de las banderas de arranque (1.6): la siembra ({@link DatabaseInit})
 * y la demo de consola ({@link DemoTriajeRunner}) solo existen si su
 * propiedad vale {@code true}.
 */
class ArranqueCondicionalTest {

    private final ApplicationContextRunner siembra = new ApplicationContextRunner()
            .withBean(RepositorioGrafoClinico.class, () -> mock(RepositorioGrafoClinico.class))
            .withBean(RepositorioBaseReglas.class, () -> mock(RepositorioBaseReglas.class))
            .withUserConfiguration(DatabaseInit.class);

    private final ApplicationContextRunner demo = new ApplicationContextRunner()
            .withBean(ServicioReglas.class, () -> mock(ServicioReglas.class))
            .withUserConfiguration(DemoTriajeRunner.class);

    @Test
    void laSiembraSeCreaConLaBanderaEncendida() {
        siembra.withPropertyValues("signbb.siembra.activa=true")
                .run(ctx -> assertEquals(1, ctx.getBeanNamesForType(DatabaseInit.class).length));
    }

    @Test
    void laSiembraNoSeCreaConLaBanderaApagadaOAusente() {
        siembra.withPropertyValues("signbb.siembra.activa=false")
                .run(ctx -> assertEquals(0, ctx.getBeanNamesForType(DatabaseInit.class).length));
        siembra.run(ctx -> assertEquals(0, ctx.getBeanNamesForType(DatabaseInit.class).length));
    }

    @Test
    void laDemoSoloSeCreaConLaBanderaEncendida() {
        demo.withPropertyValues("signbb.demo.activa=true")
                .run(ctx -> assertEquals(1, ctx.getBeanNamesForType(DemoTriajeRunner.class).length));
        demo.withPropertyValues("signbb.demo.activa=false")
                .run(ctx -> assertEquals(0, ctx.getBeanNamesForType(DemoTriajeRunner.class).length));
    }
}
