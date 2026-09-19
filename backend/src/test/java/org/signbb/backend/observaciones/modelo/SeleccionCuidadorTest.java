package org.signbb.backend.observaciones.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pruebas de validación del constructor de {@link SeleccionCuidador}.
 *
 * <p>
 * Asume JUnit 5 (Jupiter), como el resto del proyecto. Ajustar el import
 * si el proyecto usa otra versión de JUnit.
 */
class SeleccionCuidadorTest {

    @Test
    void construyeConUnaSolaOpcionParaSiNo() {
        SeleccionCuidador s = new SeleccionCuidador("r1", "n1", TipoInteraccion.SI_NO, List.of("SI"));
        assertEquals(List.of("SI"), s.getOpcionesSeleccionadas());
        assertEquals(TipoInteraccion.SI_NO, s.getTipo());
    }

    @Test
    void construyeConVariasOpcionesParaSeleccionMultiple() {
        SeleccionCuidador s = new SeleccionCuidador("r1", "n1", TipoInteraccion.SELECCION_MULTIPLE,
                List.of("A", "B"));
        assertEquals(List.of("A", "B"), s.getOpcionesSeleccionadas());
    }

    @Test
    void rechazaListaVacia() {
        assertThrows(IllegalArgumentException.class,
                () -> new SeleccionCuidador("r1", "n1", TipoInteraccion.SI_NO, List.of()));
    }

    @Test
    void rechazaVariasOpcionesParaSiNo() {
        assertThrows(IllegalArgumentException.class,
                () -> new SeleccionCuidador("r1", "n1", TipoInteraccion.SI_NO, List.of("SI", "NO")));
    }

    @Test
    void rechazaVariasOpcionesParaSeleccionUnica() {
        assertThrows(IllegalArgumentException.class,
                () -> new SeleccionCuidador("r1", "n1", TipoInteraccion.SELECCION_UNICA, List.of("A", "B")));
    }

    @Test
    void rechazaRecorridoIdVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> new SeleccionCuidador("", "n1", TipoInteraccion.SI_NO, List.of("SI")));
    }
}