package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.CatalogoDTO;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;

/** El catálogo declara el desenlace de los terminales (para colorear el destino). */
class ResolutorCatalogoNodosTest {

    private final RepositorioGrafoClinico grafos = mock(RepositorioGrafoClinico.class);
    private final ContextoClinico contexto = mock(ContextoClinico.class);
    private final RepositorioBorradorSubgrafo subgrafos = mock(RepositorioBorradorSubgrafo.class);
    private final ResolutorCatalogoNodos resolutor =
            new ResolutorCatalogoNodos(subgrafos, grafos, contexto);

    @Test
    void elCatalogoDeUnLienzoPoneElDesenlaceEnLosTerminales() {
        when(subgrafos.findByGrafoId("grafo_fiebre"))
                .thenReturn(Optional.of(LienzoFiebre.borrador(LienzoFiebre.completo())));

        CatalogoDTO catalogo = resolutor.catalogo("grafo_fiebre");

        assertEquals("URGENCIA", nodo(catalogo, LienzoFiebre.URGENCIA).desenlace());
        assertEquals("CONSULTA_MEDICA", nodo(catalogo, LienzoFiebre.CONSULTA).desenlace());
        assertEquals("RECOMENDACION", nodo(catalogo, LienzoFiebre.RECOMENDACION).desenlace());
        assertNull(nodo(catalogo, LienzoFiebre.INICIO).desenlace(), "una pregunta no tiene desenlace");
    }

    @Test
    void elCatalogoDelGrafoRaizPoneElDesenlaceEnLosTerminales() {
        CompositorGrafoClinico.Contenido compuesto = ContenidoEjemplo.compuesto();
        when(contexto.getGrafo()).thenReturn(GrafoClinicoMapper.desde(compuesto.grafo()));

        CatalogoDTO catalogo = resolutor.catalogo("grafo_raiz");

        assertTrue(catalogo.nodos().stream()
                .anyMatch(n -> "terminal".equals(n.tipo()) && n.desenlace() != null));
        assertTrue(catalogo.nodos().stream()
                .filter(n -> "pregunta".equals(n.tipo()))
                .allMatch(n -> n.desenlace() == null));
    }

    private static CatalogoDTO.NodoCatalogoDTO nodo(CatalogoDTO catalogo, String id) {
        return catalogo.nodos().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }
}
