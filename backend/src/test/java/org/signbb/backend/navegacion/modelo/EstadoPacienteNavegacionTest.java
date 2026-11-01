package org.signbb.backend.navegacion.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.FotoNavegacion;
import org.signbb.backend.navegacion.modelo.OpcionPendiente;
import org.signbb.backend.navegacion.modelo.PasoRecorrido;
import org.signbb.backend.navegacion.modelo.PasoRuta;

/**
 * Pruebas de la navegación pendiente de {@link EstadoPaciente}: cola de
 * opciones, ruta, recomendaciones y su foto para deshacer pasos.
 */
class EstadoPacienteNavegacionTest {

    private static List<PasoRuta> camino(String... ids) {
        return java.util.Arrays.stream(ids).map(id -> new PasoRuta(id, "")).toList();
    }

    @Test
    void encolarAsignaOrdenCrecienteYRechazaDestinoRepetido() {
        EstadoPaciente e = new EstadoPaciente();
        assertTrue(e.encolarOpcion("q1", "A", "d1", List.of()));
        assertTrue(e.encolarOpcion("q1", "B", "d2", List.of()));
        assertFalse(e.encolarOpcion("q2", "C", "d1", List.of()), "d1 ya está pendiente");
        // el rechazo no consume número de orden
        assertTrue(e.encolarOpcion("q2", "D", "d3", List.of()));

        List<OpcionPendiente> cola = e.cola();
        assertEquals(3, cola.size());
        assertEquals(List.of("d1", "d2", "d3"), cola.stream().map(OpcionPendiente::idDestino).toList());
        assertEquals(List.of(0, 1, 2), cola.stream().map(OpcionPendiente::orden).toList());
        assertTrue(e.colaContieneDestino("d2"));
        assertFalse(e.colaContieneDestino("nada"));
    }

    @Test
    void quitarYVaciarCola() {
        EstadoPaciente e = new EstadoPaciente();
        e.encolarOpcion("q1", "A", "d1", List.of());
        e.encolarOpcion("q1", "B", "d2", List.of());
        OpcionPendiente primera = e.cola().get(0);

        assertTrue(e.quitarDeCola(primera));
        assertEquals(1, e.cola().size());
        assertFalse(e.quitarDeCola(primera), "ya no está");

        e.vaciarCola();
        assertTrue(e.cola().isEmpty());
    }

    @Test
    void laOpcionGuardaSuCaminoYElEstadoNoExponeSuListaInterna() {
        EstadoPaciente e = new EstadoPaciente();
        e.encolarOpcion("q1", "A", "d1", camino("q1", "d1"));
        assertEquals(camino("q1", "d1"), e.cola().get(0).camino());

        e.cola().clear(); // es una copia
        assertEquals(1, e.cola().size());
    }

    @Test
    void fotoYRestauracionDevuelvenLaNavegacionAlInstanteDeLaFoto() {
        EstadoPaciente e = new EstadoPaciente();
        e.encolarOpcion("q1", "A", "d1", camino("q1", "d1"));
        e.agregarARuta(new PasoRuta("q1", ""));
        e.registrarRecomendacion("r1", camino("q1", "r1"));
        FotoNavegacion foto = e.fotografiarNavegacion();

        e.vaciarCola();
        e.encolarOpcion("q2", "B", "d9", List.of());
        e.reemplazarRuta(camino("zzz"));
        e.registrarRecomendacion("r2", List.of());

        e.restaurarNavegacion(foto);

        assertEquals(foto, e.fotografiarNavegacion());
        assertEquals(List.of("d1"), e.cola().stream().map(OpcionPendiente::idDestino).toList());
        assertEquals(List.of("r1"), List.copyOf(e.recomendaciones().keySet()));
        // el contador de orden también vuelve: la próxima opción recibe el 1
        e.encolarOpcion("q3", "C", "d5", List.of());
        assertEquals(1, e.cola().get(1).orden());
    }

    @Test
    void desapilarPasoRestauraLaFotoDeNavegacionDeEsePaso() {
        EstadoPaciente e = new EstadoPaciente();
        FotoNavegacion antes = e.fotografiarNavegacion();
        e.registrarRespuestas("p1", List.of("A", "B"));
        e.apilarPaso(new PasoRecorrido("p1", List.of("A", "B"), List.of(), List.of(), antes));
        e.encolarOpcion("p1", "B", "d1", List.of());
        e.registrarRecomendacion("r1", List.of());

        e.desapilarPaso();

        assertTrue(e.cola().isEmpty());
        assertTrue(e.recomendaciones().isEmpty());
        assertTrue(e.getRespuestas("p1").isEmpty());
    }
}
