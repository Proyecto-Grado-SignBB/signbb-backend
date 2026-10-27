package org.signbb.backend.seed;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** El seed de demo no puede ejecutarse en producción. */
class SeedDemoRunnerTest {

    @Test
    void enProduccionFallaAntesDeSembrar() {
        SeedDemoService servicio = mock(SeedDemoService.class);
        SeedDemoRunner runner = new SeedDemoRunner(servicio, "produccion");

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(servicio, org.mockito.Mockito.never()).sembrar();
    }

    @Test
    void enDesarrolloEjecutaElSeed() {
        SeedDemoService servicio = mock(SeedDemoService.class);
        SeedDemoRunner runner = new SeedDemoRunner(servicio, "desarrollo");

        runner.run(null);

        verify(servicio).sembrar();
    }
}
