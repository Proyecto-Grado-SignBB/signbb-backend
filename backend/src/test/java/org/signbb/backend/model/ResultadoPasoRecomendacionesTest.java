package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.PasoCausal;
import org.signbb.backend.navegacion.modelo.Recomendacion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.reglas.modelo.NivelAlerta;

/** La lista de recomendaciones de {@link ResultadoPaso}: vacía por defecto e inmutable. */
class ResultadoPasoRecomendacionesTest {

    private static final Nodo RECO = new Nodo("r1", "recomendacion", "Recomendación 1", 30);
    private static final Nodo OTRA = new Nodo("r2", "recomendacion", "Recomendación 2", 20);

    @Test
    void porDefectoNoHayRecomendaciones() {
        assertTrue(ResultadoPaso.pregunta(new Nodo("q", "pregunta", "?")).recomendaciones().isEmpty());
        assertTrue(ResultadoPaso.terminal(RECO, NivelAlerta.NORMAL, List.of(), List.of())
                .recomendaciones().isEmpty());
    }

    @Test
    void elResultadoNormalPuedeLlevarVariasRecomendacionesConSuCamino() {
        List<Recomendacion> recs = new ArrayList<>(List.of(
                new Recomendacion(RECO, List.of(new PasoCausal(RECO, "A"))),
                new Recomendacion(OTRA, List.of())));
        ResultadoPaso r = ResultadoPaso.terminal(RECO, NivelAlerta.NORMAL, List.of(), List.of(), recs);

        recs.clear(); // la lista guardada es una copia
        assertEquals(2, r.recomendaciones().size());
        assertEquals("r1", r.recomendaciones().get(0).nodo().getId());
        assertEquals("A", r.recomendaciones().get(0).camino().get(0).condicion());
        assertThrows(UnsupportedOperationException.class, () -> r.recomendaciones().add(null));
    }

    @Test
    void unaRecomendacionNecesitaNodo() {
        assertThrows(IllegalArgumentException.class, () -> new Recomendacion(null, List.of()));
    }
}
