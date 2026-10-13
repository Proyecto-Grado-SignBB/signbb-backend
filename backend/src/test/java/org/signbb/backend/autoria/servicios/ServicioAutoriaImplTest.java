package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.CategoriaDTO;
import org.signbb.backend.autoria.controladores.DespuesDeRequest;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.OpcionRequest;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.CopiaPublicada;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

/**
 * Tests de {@link ServicioAutoriaImpl} con repositorios simulados: categorías,
 * creación del borrador, estados y que cada edición se guarde.
 */
class ServicioAutoriaImplTest {

    private RepositorioBorradorSubgrafo borradores;
    private PublicadorSubgrafo publicador;
    private ServicioAutoriaImpl servicio;

    @BeforeEach
    void preparar() {
        List<GrafoClinicoEntity> filas = new ArrayList<>();
        filas.add(fila(ContenidoEjemplo.raiz()));
        ContenidoEjemplo.subgrafos().forEach(s -> filas.add(fila(s)));
        RepositorioGrafoClinico grafos = mock(RepositorioGrafoClinico.class);
        when(grafos.findAll()).thenReturn(filas);

        borradores = mock(RepositorioBorradorSubgrafo.class);
        when(borradores.save(any(BorradorSubgrafoEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(borradores.existsByGrafoId(anyString())).thenReturn(false);
        RepositorioBorradorBaseRegla basesReglas = mock(RepositorioBorradorBaseRegla.class);
        when(basesReglas.findByGrafoId(anyString())).thenReturn(Optional.empty());
        publicador = mock(PublicadorSubgrafo.class);
        servicio = new ServicioAutoriaImpl(borradores, basesReglas, grafos, publicador);
    }

    private static GrafoClinicoEntity fila(GrafoClinicoJson documento) {
        GrafoClinicoEntity e = new GrafoClinicoEntity();
        e.setEstructura(documento);
        return e;
    }

    private BorradorSubgrafoEntity guardado(BorradorSubgrafoEntity b) {
        when(borradores.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void lasCategoriasSonLasPreguntasPuenteDeLaRaiz() {
        List<CategoriaDTO> categorias = servicio.categorias();

        // Piel y temperatura se ofrecen aunque todavía no tengan subgrafos.
        assertEquals(List.of("puente_piel", "puente_temperatura", "puente_respiracion", "puente_gastro",
                "puente_neuro"), categorias.stream().map(CategoriaDTO::puenteId).toList());
        assertEquals(List.of("piel", "temperatura", "respiracion", "gastrointestinal", "neurologico"),
                categorias.stream().map(CategoriaDTO::nombre).toList());
        assertTrue(categorias.get(0).pregunta().startsWith("¿Qué cambio observa en la piel"));
    }

    @Test
    void crearArmaUnBorradorVacioConUnGrafoIdSinTildes() {
        SubgrafoDTO dto = servicio.crear(new SubgrafoRequest("  Fiebre en niños pequeños ", "puente_neuro",
                " Fiebre o calentura ", "Dra. Prueba", ""));

        assertEquals("grafo_fiebre_en_ninos_pequenos", dto.grafoId());
        assertEquals("Fiebre en niños pequeños", dto.nombre());
        assertEquals("neurologico", dto.categoria());
        assertEquals("Fiebre o calentura", dto.etiquetaOpcion());
        assertEquals(EstadoBorrador.BORRADOR, dto.estado());
        assertTrue(dto.nodos().isEmpty());
        assertEquals(1, dto.avisos().size(), "solo falta la primera pregunta");
        assertNotNull(dto.creadoEn());
    }

    @Test
    void elGrafoIdNoChocaConLosGrafosNiLosBorradores() {
        when(borradores.existsByGrafoId("grafo_respiracion_2")).thenReturn(true);

        SubgrafoDTO dto = servicio.crear(new SubgrafoRequest("Respiración", "puente_respiracion", "Le cuesta respirar", null, null));

        assertEquals("grafo_respiracion_3", dto.grafoId());
    }

    @Test
    void crearSinNombreOConUnaCategoriaQueNoExisteSeRechaza() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(new SubgrafoRequest(" ", "puente_neuro", "Fiebre", null, null)));
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(new SubgrafoRequest("Fiebre", "puente_x", "Fiebre", null, null)));
        verify(borradores, never()).save(any());
    }

    @Test
    void sinNombreParaLaFamiliaSeRechaza() {
        EdicionInvalidaException e = assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(new SubgrafoRequest("Ictericia en la piel", "puente_piel", "  ", null, null)));

        assertTrue(e.getMessage().contains("familias"));
        verify(borradores, never()).save(any());
    }

    @Test
    void elMasSeGuardaYDevuelveElLienzoCompleto() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.primeraPregunta()));

        SubgrafoDTO dto = servicio.agregarNodo(1L, new NodoRequest(
                new DespuesDeRequest(LienzoFiebre.INICIO, LienzoFiebre.MAS_39), TipoNodo.CONCLUSION,
                "Vaya a urgencias", null, Desenlace.URGENCIA, null));

        verify(borradores).save(b);
        assertEquals(2, dto.nodos().size());
        assertEquals(2, dto.avisos().size(), "quedan dos \"+\"");
    }

    @Test
    void laPrimeraPreguntaSeCreaSinDespuesDe() {
        guardado(LienzoFiebre.borrador(DocumentoLienzo.vacio()));

        SubgrafoDTO dto = servicio.agregarNodo(1L, new NodoRequest(null, TipoNodo.PREGUNTA, "¿Fiebre?", true, null,
                List.of(new OpcionRequest(null, "Sí", Gravedad.GRAVE), new OpcionRequest(null, "No", null))));

        assertEquals("pregunta_1", dto.inicio());
        assertTrue(dto.nodos().get(0).variasRespuestas());
    }

    @Test
    void editarUnBorradorPublicadoLoDejaConCambiosSinPublicar() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        b.setEstado(EstadoBorrador.PUBLICADO);
        b.setPublicadoEn(Instant.parse("2026-10-01T00:00:00Z"));

        SubgrafoDTO dto = servicio.enlazar(1L, LienzoFiebre.MENOS_38, null);

        assertEquals(EstadoBorrador.CAMBIOS_SIN_PUBLICAR, dto.estado());
    }

    @Test
    void publicarMarcaElBorradorComoPublicado() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));

        SubgrafoDTO dto = servicio.publicar(1L);

        verify(publicador).publicar(eq(b), any(Instant.class));
        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());
        assertNotNull(dto.publicadoEn());
    }

    @Test
    void publicarGuardaUnaCopiaDeLoPublicado() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));

        SubgrafoDTO dto = servicio.publicar(1L);

        assertTrue(dto.puedeDescartar());
        CopiaPublicada copia = b.getCopiaPublicada();
        assertEquals(LienzoFiebre.completo(), copia.documento());
        assertEquals("Fiebre", copia.nombre());
        assertEquals("puente_neuro", copia.puenteId());
        assertEquals("Fiebre o temperatura alta", copia.etiquetaOpcion());
    }

    @Test
    void descartarDevuelveElLienzoYLosDatosAComoEstabanPublicados() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);
        servicio.enlazar(1L, LienzoFiebre.MENOS_38, null);
        servicio.actualizar(1L, new SubgrafoRequest("Otro nombre", "puente_respiracion", "Otra opción", null, null));
        assertEquals(EstadoBorrador.CAMBIOS_SIN_PUBLICAR, b.getEstado());

        SubgrafoDTO dto = servicio.descartarCambios(1L);

        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());
        assertEquals(LienzoFiebre.completo(), b.getDocumento());
        assertEquals("Fiebre", dto.nombre());
        assertEquals("puente_neuro", dto.puenteId());
        assertEquals("neurologico", dto.categoria());
        assertEquals("Fiebre o temperatura alta", dto.etiquetaOpcion());
    }

    @Test
    void descartarSinCambiosSinPublicarDevuelveElBorradorIgual() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);

        SubgrafoDTO dto = servicio.descartarCambios(1L);

        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());
        assertEquals(LienzoFiebre.completo(), b.getDocumento());
        verify(borradores, times(1)).save(any(BorradorSubgrafoEntity.class)); // solo el de publicar
    }

    @Test
    void descartarUnBorradorQueNuncaSePublicoSeRechaza() {
        guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));

        assertThrows(EdicionInvalidaException.class, () -> servicio.descartarCambios(1L));
        verify(borradores, never()).save(any());
    }

    @Test
    void descartarUnPublicadoSinCopiaSeRechazaYNoCambiaNada() {
        // Publicado antes de que existiera la copia: no hay a qué volver.
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        b.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        b.setPublicadoEn(Instant.parse("2026-10-01T00:00:00Z"));

        assertThrows(EdicionInvalidaException.class, () -> servicio.descartarCambios(1L));
        assertEquals(EstadoBorrador.CAMBIOS_SIN_PUBLICAR, b.getEstado());
        verify(borradores, never()).save(any());
    }

    @Test
    void unBorradorPublicadoNoSePuedeBorrar() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        b.setPublicadoEn(Instant.parse("2026-10-01T00:00:00Z"));

        assertThrows(EdicionInvalidaException.class, () -> servicio.eliminar(1L));
        verify(borradores, never()).delete(any());
    }

    @Test
    void despublicarRetiraElSubgrafoYDevuelveElBorradorASuEstadoInicial() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);

        SubgrafoDTO dto = servicio.despublicar(1L);

        verify(publicador).retirar(b);
        assertEquals(EstadoBorrador.BORRADOR, dto.estado());
        assertNull(b.getPublicadoEn());
        assertNull(b.getCopiaPublicada());
        assertFalse(dto.puedeDescartar());
    }

    @Test
    void despublicarNoTocaElLienzoNiLosDatosDelBorrador() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);
        servicio.actualizar(1L, new SubgrafoRequest("Otro nombre", "puente_respiracion", "Otra opción", null, null));

        SubgrafoDTO dto = servicio.despublicar(1L);

        assertEquals(LienzoFiebre.completo(), b.getDocumento());
        assertEquals("Otro nombre", dto.nombre());
        assertEquals("Otra opción", dto.etiquetaOpcion());
    }

    @Test
    void despublicarUnPublicadoConCambiosSinPublicarTambienFunciona() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);
        servicio.enlazar(1L, LienzoFiebre.MENOS_38, null);
        assertEquals(EstadoBorrador.CAMBIOS_SIN_PUBLICAR, b.getEstado());

        SubgrafoDTO dto = servicio.despublicar(1L);

        assertEquals(EstadoBorrador.BORRADOR, dto.estado());
    }

    @Test
    void despublicarUnBorradorQueNuncaSePublicoSeRechazaYNoRetiraNada() {
        guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));

        assertThrows(EdicionInvalidaException.class, () -> servicio.despublicar(1L));
        verify(publicador, never()).retirar(any());
        verify(borradores, never()).save(any());
    }

    @Test
    void siElContenidoQueQuedaNoEsValidoElBorradorSigueSiendoPublicado() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);
        doThrow(new ContenidoInvalidoException("Este es el único subgrafo publicado", List.of()))
                .when(publicador).retirar(b);

        assertThrows(ContenidoInvalidoException.class, () -> servicio.despublicar(1L));
        assertEquals(EstadoBorrador.PUBLICADO, b.getEstado());
        assertNotNull(b.getPublicadoEn());
        assertNotNull(b.getCopiaPublicada());
    }

    @Test
    void despublicarUnBorradorQueNoExisteDa404() {
        when(borradores.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.despublicar(9L));
    }

    @Test
    void unBorradorDespublicadoSePuedeVolverAPublicarYYaSePuedeBorrar() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);
        servicio.despublicar(1L);

        SubgrafoDTO dto = servicio.publicar(1L);
        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());

        servicio.despublicar(1L);
        servicio.eliminar(1L);
        verify(borradores).delete(b);
    }

    @Test
    void unBorradorQueNoExisteDa404() {
        when(borradores.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(9L));
    }
}
