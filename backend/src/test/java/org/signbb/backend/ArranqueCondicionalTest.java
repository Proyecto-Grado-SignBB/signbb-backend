package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.reglas.servicios.ServicioReglas;
import org.signbb.backend.seed.SeedDemoRunner;
import org.signbb.backend.seed.SeedDemoService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests de las banderas de arranque (1.6): la siembra ({@link DatabaseInit}),
 * la demo de consola ({@link DemoTriajeRunner}) y el seed de demo
 * ({@link SeedDemoRunner}) solo existen si su propiedad vale {@code true}.
 */
class ArranqueCondicionalTest {

    private final ApplicationContextRunner siembra = new ApplicationContextRunner()
            .withBean(RepositorioGrafoClinico.class, () -> mock(RepositorioGrafoClinico.class))
            .withBean(ResolvedorContenidoClinico.class, () -> mock(ResolvedorContenidoClinico.class))
            .withUserConfiguration(DatabaseInit.class);

    private final ApplicationContextRunner demo = new ApplicationContextRunner()
            .withBean(ServicioReglas.class, () -> mock(ServicioReglas.class))
            .withUserConfiguration(DemoTriajeRunner.class);

    private final ApplicationContextRunner seed = new ApplicationContextRunner()
            .withBean(SeedDemoService.class, () -> mock(SeedDemoService.class))
            .withUserConfiguration(SeedDemoRunner.class);

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

    @Test
    void elSeedDemoSoloSeCreaConLaBanderaEncendida() {
        seed.withPropertyValues("signbb.seed.demo.activa=true")
                .run(ctx -> assertEquals(1, ctx.getBeanNamesForType(SeedDemoRunner.class).length));
        seed.withPropertyValues("signbb.seed.demo.activa=false")
                .run(ctx -> assertEquals(0, ctx.getBeanNamesForType(SeedDemoRunner.class).length));
        seed.run(ctx -> assertEquals(0, ctx.getBeanNamesForType(SeedDemoRunner.class).length));
    }
}
