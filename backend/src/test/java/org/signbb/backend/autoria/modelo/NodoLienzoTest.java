package org.signbb.backend.autoria.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/** El lienzo se guarda como JSON: los guardados antes de las imágenes tienen que seguir leyéndose. */
class NodoLienzoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unNodoGuardadoSinImagenesSeLeeConLaListaVacia() throws Exception {
        String json = "{\"id\":\"pregunta_1\",\"tipo\":\"PREGUNTA\",\"texto\":\"¿Fiebre?\","
                + "\"variasRespuestas\":false,\"desenlace\":null,\"respuestas\":[]}";

        NodoLienzo nodo = mapper.readValue(json, NodoLienzo.class);

        assertEquals("pregunta_1", nodo.id());
        assertTrue(nodo.imagenes().isEmpty());
    }

    @Test
    void lasImagenesDeUnNodoSeGuardanYSeLeenDeNuevo() throws Exception {
        String imagen = "11111111-1111-1111-1111-111111111111.png";
        NodoLienzo nodo = new NodoLienzo("pregunta_1", TipoNodo.PREGUNTA, "¿Fiebre?", false, null, List.of(),
                List.of(imagen));

        NodoLienzo leido = mapper.readValue(mapper.writeValueAsString(nodo), NodoLienzo.class);

        assertEquals(List.of(imagen), leido.imagenes());
    }
}
