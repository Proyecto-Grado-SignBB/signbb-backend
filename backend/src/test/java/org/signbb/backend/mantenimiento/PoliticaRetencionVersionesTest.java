package org.signbb.backend.mantenimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.signbb.backend.mantenimiento.PoliticaRetencionVersiones.VersionConFecha;

/**
 * Tests de {@link PoliticaRetencionVersiones}: se conservan las 20 más
 * recientes
 * y las reemplazadas hace menos de 25 h, y nunca la vigente.
 */
class PoliticaRetencionVersionesTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T03:30:00Z");

    /**
     * Versiones 1..n, una publicada cada {@code cada}; la última (n) se guardó
     * {@code hace} antes de AHORA.
     */
    private static List<VersionConFecha> versiones(int n, Duration hace, Duration cada) {
        List<VersionConFecha> lista = new ArrayList<>();
        for (int id = 1; id <= n; id++) {
            lista.add(new VersionConFecha((long) id, AHORA.minus(hace).minus(cada.multipliedBy(n - id))));
        }
        return lista;
    }

    @Test
    void conVeinteOMenosNoSeBorraNada() {
        assertTrue(PoliticaRetencionVersiones.aBorrar(List.of(), AHORA).isEmpty());
        assertTrue(PoliticaRetencionVersiones.aBorrar(versiones(1, Duration.ofDays(90), Duration.ofDays(1)), AHORA)
                .isEmpty());
        assertTrue(PoliticaRetencionVersiones.aBorrar(versiones(20, Duration.ofDays(90), Duration.ofDays(1)), AHORA)
                .isEmpty());
    }

    @Test
    void conMasDeVeinteViejasSeBorranLasMasAntiguas() {
        List<Long> borrar = PoliticaRetencionVersiones.aBorrar(versiones(25, Duration.ofDays(30), Duration.ofDays(1)),
                AHORA);

        assertEquals(List.of(5L, 4L, 3L, 2L, 1L), borrar);
    }

    @Test
    void laVigenteYLasVeinteMasRecientesNuncaSeBorran() {
        List<Long> borrar = PoliticaRetencionVersiones.aBorrar(versiones(500, Duration.ofDays(365), Duration.ofDays(1)),
                AHORA);

        assertEquals(480, borrar.size());
        assertFalse(borrar.contains(500L));
        LongStream.rangeClosed(481, 500).forEach(id -> assertFalse(borrar.contains(id)));
        assertTrue(borrar.contains(480L));
    }

    @Test
    void unaVersionReemplazadaHaceMenosDeVeinticincoHorasSeConserva() {
        // 25 versiones publicadas con minutos de diferencia, la última hace 2 h:
        // las 5 sobrantes se reemplazaron hace menos de 25 h, así que se quedan.
        List<VersionConFecha> todas = versiones(25, Duration.ofHours(2), Duration.ofMinutes(10));

        assertTrue(PoliticaRetencionVersiones.aBorrar(todas, AHORA).isEmpty());
    }

    @Test
    void conservaLasReemplazadasRecientementeYBorraLasQueYaPasaronLaGracia() {
        // La versión 1 la reemplazó la 2 hace 30 h; la 2 la reemplazó la 3 hace 20 h.
        List<VersionConFecha> todas = new ArrayList<>();
        todas.add(new VersionConFecha(1L, AHORA.minus(Duration.ofDays(5))));
        todas.add(new VersionConFecha(2L, AHORA.minus(Duration.ofHours(30))));
        todas.add(new VersionConFecha(3L, AHORA.minus(Duration.ofHours(20))));
        for (long id = 4; id <= 22; id++) {
            todas.add(new VersionConFecha(id, AHORA.minus(Duration.ofHours(19)).plus(Duration.ofMinutes(id))));
        }
        // 22 versiones: las sobrantes son la 1 y la 2.

        assertEquals(List.of(1L), PoliticaRetencionVersiones.aBorrar(todas, AHORA));
    }

    @Test
    void justoEnElLimiteDeLaGraciaSeConservaYUnSegundoDespuesSeBorra() {
        // 21 versiones: solo la 1 sobra, y la reemplazó la 2.
        assertTrue(PoliticaRetencionVersiones.aBorrar(veintiunaConLaSegundaCreada(Duration.ofHours(25)), AHORA)
                .isEmpty(), "reemplazada hace exactamente 25 h: todavía se conserva");
        assertEquals(List.of(1L), PoliticaRetencionVersiones.aBorrar(
                veintiunaConLaSegundaCreada(Duration.ofHours(25).plusSeconds(1)), AHORA));
    }

    private static List<VersionConFecha> veintiunaConLaSegundaCreada(Duration hace) {
        List<VersionConFecha> todas = new ArrayList<>();
        todas.add(new VersionConFecha(1L, AHORA.minus(Duration.ofDays(5))));
        todas.add(new VersionConFecha(2L, AHORA.minus(hace)));
        for (long id = 3; id <= 21; id++) {
            todas.add(new VersionConFecha(id, AHORA.minus(Duration.ofHours(10)).plus(Duration.ofMinutes(id))));
        }
        return todas;
    }

    @Test
    void noImportaElOrdenEnQueLleguenLasVersiones() {
        List<VersionConFecha> todas = versiones(25, Duration.ofDays(30), Duration.ofDays(1));
        Collections.shuffle(todas, new java.util.Random(7));

        assertEquals(List.of(5L, 4L, 3L, 2L, 1L), PoliticaRetencionVersiones.aBorrar(todas, AHORA));
    }
}
