package org.signbb.backend.mantenimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.CopiaPublicada;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.media.AlmacenImagenes;
import org.signbb.backend.media.AlmacenImagenes.Guardada;
import org.signbb.backend.media.AlmacenNoDisponibleException;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

/**
 * Tests de {@link LimpiadorImagenes}: borra solo lo que no usa nadie, lo que
 * tiene más de 24 h y lo que tiene nombre de imagen del servidor; ante la duda
 * no borra.
 */
class LimpiadorImagenesTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T03:30:00Z");
    private static final Instant VIEJA = AHORA.minus(Duration.ofDays(3));

    private static final String SIN_USO = "00000000-0000-0000-0000-000000000001.png";
    private static final String EN_LIENZO = "00000000-0000-0000-0000-000000000002.jpg";
    private static final String EN_COPIA = "00000000-0000-0000-0000-000000000003.webp";
    private static final String EN_GRAFO = "00000000-0000-0000-0000-000000000004.gif";
    private static final String EN_VERSION = "00000000-0000-0000-0000-000000000005.png";

    private AlmacenImagenes almacen;
    private RepositorioBorradorSubgrafo borradores;
    private RepositorioGrafoClinico grafos;
    private RepositorioVersionContenido versiones;
    private LimpiadorImagenes limpiador;

    @BeforeEach
    void preparar() {
        almacen = mock(AlmacenImagenes.class);
        borradores = mock(RepositorioBorradorSubgrafo.class);
        grafos = mock(RepositorioGrafoClinico.class);
        versiones = mock(RepositorioVersionContenido.class);
        when(grafos.count()).thenReturn(4L);
        when(borradores.findAll()).thenReturn(List.of());
        when(grafos.findAll()).thenReturn(List.of());
        when(versiones.findAll()).thenReturn(List.of());
        limpiador = new LimpiadorImagenes(almacen, borradores, grafos, versiones);
    }

    private void guardadas(Guardada... g) {
        when(almacen.listar()).thenReturn(List.of(g));
    }

    private static Guardada vieja(String nombre) {
        return new Guardada(nombre, VIEJA);
    }

    private static DocumentoLienzo lienzoCon(String imagen) {
        NodoLienzo nodo = new NodoLienzo("pregunta_1", TipoNodo.PREGUNTA, "¿Tiene fiebre?", false, null, List.of(),
                List.of(imagen));
        return new DocumentoLienzo("pregunta_1", 1, Map.of("pregunta_1", nodo));
    }

    private static GrafoClinicoJson grafoCon(String imagen) {
        GrafoClinicoJson.NodoJson nodo = new GrafoClinicoJson.NodoJson("¿Tiene fiebre?",
                new GrafoClinicoJson.MetadataNodo("pregunta", null, null, null, 1, true, List.of(imagen)));
        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo("grafo_x", "x", true, List.of(), "pregunta_1",
                "pregunta_1", Map.of(), Map.of("pregunta_1", nodo), List.of()));
    }

    private static BorradorSubgrafoEntity borrador(DocumentoLienzo documento, CopiaPublicada copia) {
        BorradorSubgrafoEntity b = new BorradorSubgrafoEntity();
        b.setDocumento(documento);
        b.setCopiaPublicada(copia);
        return b;
    }

    private void hayContenidoEnUso() {
        DocumentoLienzo conCopia = lienzoCon(EN_LIENZO);
        CopiaPublicada copia = new CopiaPublicada(lienzoCon(EN_COPIA), "Fiebre", "puente_neuro", "neurologico",
                "Fiebre", null, null);
        when(borradores.findAll()).thenReturn(List.of(borrador(conCopia, copia)));
        GrafoClinicoEntity fila = new GrafoClinicoEntity();
        fila.setEstructura(grafoCon(EN_GRAFO));
        when(grafos.findAll()).thenReturn(List.of(fila));
        when(versiones.findAll()).thenReturn(List.of(new VersionContenidoEntity(grafoCon(EN_VERSION), null, "t")));
    }

    @Test
    void borraLaQueNadieUsa() {
        guardadas(vieja(SIN_USO));

        List<String> borradas = limpiador.limpiar(AHORA);

        assertEquals(List.of(SIN_USO), borradas);
        verify(almacen).eliminar(SIN_USO);
    }

    @Test
    void conservaLasQueUsaUnLienzoUnaCopiaPublicadaUnGrafoOUnaVersion() {
        hayContenidoEnUso();
        guardadas(vieja(EN_LIENZO), vieja(EN_COPIA), vieja(EN_GRAFO), vieja(EN_VERSION), vieja(SIN_USO));

        List<String> borradas = limpiador.limpiar(AHORA);

        assertEquals(List.of(SIN_USO), borradas);
        verify(almacen, never()).eliminar(EN_LIENZO);
        verify(almacen, never()).eliminar(EN_COPIA);
        verify(almacen, never()).eliminar(EN_GRAFO);
        verify(almacen, never()).eliminar(EN_VERSION);
    }

    @Test
    void nuncaBorraLasSubidasHaceMenosDeVeinticuatroHoras() {
        guardadas(new Guardada(SIN_USO, AHORA.minus(Duration.ofHours(23))),
                new Guardada("00000000-0000-0000-0000-000000000009.png", AHORA.minus(Duration.ofHours(24)).plusSeconds(1)));

        assertTrue(limpiador.limpiar(AHORA).isEmpty());
        verify(almacen, never()).eliminar(anyString());
    }

    @Test
    void pasadasVeinticuatroHorasYaSePuedeBorrar() {
        guardadas(new Guardada(SIN_USO, AHORA.minus(Duration.ofHours(24)).minusSeconds(1)));

        assertEquals(List.of(SIN_USO), limpiador.limpiar(AHORA));
    }

    @Test
    void noTocaArchivosQueNoTienenNombreDeImagenDelServidor() {
        guardadas(vieja("logo.png"), vieja("notas.txt"), vieja("carpeta/" + SIN_USO), vieja(SIN_USO.toUpperCase()));

        assertTrue(limpiador.limpiar(AHORA).isEmpty());
        verify(almacen, never()).eliminar(anyString());
    }

    @Test
    void sinGrafosEnLaBaseNoBorraNadaPorqueNoSabeQueSeUsa() {
        when(grafos.count()).thenReturn(0L);
        guardadas(vieja(SIN_USO));

        assertTrue(limpiador.limpiar(AHORA).isEmpty());
        verify(almacen, never()).eliminar(anyString());
    }

    @Test
    void sinCandidatasNiSiquieraLeeLaBase() {
        guardadas(new Guardada(SIN_USO, AHORA.minus(Duration.ofHours(1))));

        limpiador.limpiar(AHORA);

        verify(borradores, never()).findAll();
        verify(grafos, never()).findAll();
        verify(versiones, never()).findAll();
    }

    @Test
    void siSiloNoRespondeAlListarSePropagaYNoSeBorraNada() {
        when(almacen.listar()).thenThrow(new AlmacenNoDisponibleException("sin silo", new RuntimeException()));

        assertThrows(AlmacenNoDisponibleException.class, () -> limpiador.limpiar(AHORA));
        verify(almacen, never()).eliminar(anyString());
    }

    @Test
    void siFallaBorrarUnaSigueConLasDemas() {
        String otra = "00000000-0000-0000-0000-000000000006.png";
        guardadas(vieja(SIN_USO), vieja(otra));
        doThrow(new AlmacenNoDisponibleException("sin silo", new RuntimeException())).when(almacen)
                .eliminar(SIN_USO);

        List<String> borradas = limpiador.limpiar(AHORA);

        assertEquals(List.of(otra), borradas);
        verify(almacen).eliminar(otra);
    }

    @Test
    void siNoSePuedeLeerUnDocumentoNoBorraNada() {
        guardadas(vieja(SIN_USO));
        when(borradores.findAll()).thenThrow(new IllegalStateException("base caída"));

        assertThrows(IllegalStateException.class, () -> limpiador.limpiar(AHORA));
        verify(almacen, never()).eliminar(any());
    }

    @Test
    void lasImagenesDeVariosBorradoresSeJuntan() {
        List<BorradorSubgrafoEntity> muchos = new ArrayList<>();
        muchos.add(borrador(lienzoCon(EN_LIENZO), null));
        muchos.add(borrador(lienzoCon(EN_COPIA), null));
        when(borradores.findAll()).thenReturn(muchos);
        guardadas(vieja(EN_LIENZO), vieja(EN_COPIA), vieja(SIN_USO));

        assertEquals(List.of(SIN_USO), limpiador.limpiar(AHORA));
    }
}
