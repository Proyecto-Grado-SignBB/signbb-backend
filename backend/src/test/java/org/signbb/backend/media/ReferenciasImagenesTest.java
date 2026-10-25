package org.signbb.backend.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.CopiaPublicada;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

/**
 * Tests de {@link ReferenciasImagenes}: qué imágenes son públicas (grafos y
 * versiones) y qué imágenes están en uso (además, los borradores).
 */
class ReferenciasImagenesTest {

    private static final String EN_BORRADOR = "11111111-1111-1111-1111-111111111111.png";
    private static final String EN_COPIA = "22222222-2222-2222-2222-222222222222.jpg";
    private static final String EN_GRAFO = "33333333-3333-3333-3333-333333333333.webp";
    private static final String EN_VERSION = "44444444-4444-4444-4444-444444444444.gif";

    private RepositorioBorradorSubgrafo borradores;
    private RepositorioGrafoClinico grafos;
    private RepositorioVersionContenido versiones;
    private ReferenciasImagenes referencias;

    @BeforeEach
    void preparar() {
        borradores = mock(RepositorioBorradorSubgrafo.class);
        grafos = mock(RepositorioGrafoClinico.class);
        versiones = mock(RepositorioVersionContenido.class);
        referencias = new ReferenciasImagenes(borradores, grafos, versiones);
    }

    @Test
    void unaImagenDeBorradorNoEsPublicaPeroSiEstaEnUso() {
        when(borradores.findAll()).thenReturn(List.of(borrador(EN_BORRADOR, null)));
        when(grafos.findAll()).thenReturn(List.of());
        when(versiones.findAll()).thenReturn(List.of());

        assertFalse(referencias.esPublicada(EN_BORRADOR));
        assertTrue(referencias.enUso().contains(EN_BORRADOR));
    }

    @Test
    void laCopiaPublicadaDeUnBorradorCuentaComoEnUsoPeroLaRutaPublicaSoloMiraElGrafo() {
        when(borradores.findAll()).thenReturn(List.of(borrador(null, EN_COPIA)));
        when(grafos.findAll()).thenReturn(List.of());
        when(versiones.findAll()).thenReturn(List.of());

        assertTrue(referencias.enUso().contains(EN_COPIA));
        assertFalse(referencias.esPublicada(EN_COPIA));
    }

    @Test
    void lasImagenesDeGrafosYVersionesSonPublicas() {
        when(borradores.findAll()).thenReturn(List.of());
        when(grafos.findAll()).thenReturn(List.of(grafo(EN_GRAFO)));
        when(versiones.findAll()).thenReturn(List.of(version(EN_VERSION)));

        assertEquals(Set.of(EN_GRAFO, EN_VERSION), referencias.publicadas());
        assertTrue(referencias.esPublicada(EN_GRAFO));
        assertTrue(referencias.esPublicada(EN_VERSION));
    }

    @Test
    void enUsoIncluyeBorradoresPublicadosYVersiones() {
        when(borradores.findAll()).thenReturn(List.of(borrador(EN_BORRADOR, EN_COPIA)));
        when(grafos.findAll()).thenReturn(List.of(grafo(EN_GRAFO)));
        when(versiones.findAll()).thenReturn(List.of(version(EN_VERSION)));

        assertEquals(Set.of(EN_BORRADOR, EN_COPIA, EN_GRAFO, EN_VERSION), referencias.enUso());
    }

    @Test
    void laListaDePublicadasSeCacheaHastaInvalidar() {
        when(grafos.findAll()).thenReturn(List.of(grafo(EN_GRAFO)));
        when(versiones.findAll()).thenReturn(List.of(version(EN_VERSION)));

        referencias.publicadas();
        referencias.publicadas();
        verify(grafos, times(1)).findAll();
        verify(versiones, times(1)).findAll();

        referencias.invalidar();
        referencias.publicadas();
        verify(grafos, times(2)).findAll();
        verify(versiones, times(2)).findAll();
    }

    @Test
    void hayGrafosPublicadosDependeDeLaTablaDeGrafos() {
        when(grafos.count()).thenReturn(0L);
        assertFalse(referencias.hayGrafosPublicados());

        when(grafos.count()).thenReturn(2L);
        assertTrue(referencias.hayGrafosPublicados());
    }

    private static BorradorSubgrafoEntity borrador(String imagenLienzo, String imagenCopia) {
        BorradorSubgrafoEntity b = new BorradorSubgrafoEntity();
        b.setDocumento(documento(imagenLienzo));
        if (imagenCopia != null) {
            b.setCopiaPublicada(new CopiaPublicada(documento(imagenCopia), "Subgrafo", "puente",
                    "categoria", "opcion", "Autora", "Institución"));
        }
        return b;
    }

    private static DocumentoLienzo documento(String imagen) {
        if (imagen == null) {
            return DocumentoLienzo.vacio();
        }
        NodoLienzo nodo = new NodoLienzo("pregunta_1", TipoNodo.PREGUNTA, "¿Tiene manchas?", false, null,
                List.of(), List.of(imagen));
        return new DocumentoLienzo("pregunta_1", 1, Map.of("pregunta_1", nodo));
    }

    private static GrafoClinicoEntity grafo(String imagen) {
        GrafoClinicoEntity g = new GrafoClinicoEntity();
        g.setEstructura(grafoJson(imagen));
        return g;
    }

    private static VersionContenidoEntity version(String imagen) {
        return new VersionContenidoEntity(grafoJson(imagen), null, "prueba");
    }

    private static GrafoClinicoJson grafoJson(String imagen) {
        GrafoClinicoJson.MetadataNodo metadata = new GrafoClinicoJson.MetadataNodo("pregunta", "opciones",
                null, null, 1, true, List.of(imagen));
        GrafoClinicoJson.NodoJson nodo = new GrafoClinicoJson.NodoJson("¿Tiene manchas?", metadata);
        GrafoClinicoJson.Grafo graph = new GrafoClinicoJson.Grafo("grafo", "Grafo", true, List.of(),
                "pregunta_1", null, Map.of(), Map.of("pregunta_1", nodo), List.of());
        return new GrafoClinicoJson(graph);
    }
}
