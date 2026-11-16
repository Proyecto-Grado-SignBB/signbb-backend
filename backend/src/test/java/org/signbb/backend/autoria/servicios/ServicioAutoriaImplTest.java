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

import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_A;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.signbb.backend.autoria.controladores.CategoriaDTO;
import org.signbb.backend.autoria.controladores.DespuesDeRequest;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.OpcionRequest;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.CopiaBaseReglaPublicada;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.CopiaPublicada;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.media.ServicioImagenes;
import org.signbb.backend.seguridad.UsuariosPrueba;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

/**
 * Tests de {@link ServicioAutoriaImpl} con repositorios simulados: categorías,
 * creación del borrador, estados y que cada edición se guarde.
 */
class ServicioAutoriaImplTest {

    private static final String IMAGEN_1 = "11111111-1111-1111-1111-111111111111.png";
    private static final String IMAGEN_2 = "22222222-2222-2222-2222-222222222222.jpg";
    private static final String IMAGEN_3 = "33333333-3333-3333-3333-333333333333.webp";

    private RepositorioBorradorSubgrafo borradores;
    private RepositorioBorradorBaseRegla basesReglas;
    private RepositorioBaseReglas basesPublicadas;
    private PublicadorSubgrafo publicador;
    private PublicadorRegla publicadorRegla;
    private ResolvedorVersiones versiones;
    private ServicioImagenes imagenes;
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
        basesReglas = mock(RepositorioBorradorBaseRegla.class);
        when(basesReglas.findByGrafoId(anyString())).thenReturn(Optional.empty());
        when(basesReglas.save(any(BorradorBaseReglaEntity.class))).thenAnswer(i -> i.getArgument(0));
        basesPublicadas = mock(RepositorioBaseReglas.class);
        publicador = mock(PublicadorSubgrafo.class);
        when(publicador.publicar(any(), any())).thenReturn(ContenidoEjemplo.compuesto());
        publicadorRegla = mock(PublicadorRegla.class);
        when(publicadorRegla.publicar(any(), any())).thenReturn(ContenidoEjemplo.compuesto());
        versiones = mock(ResolvedorVersiones.class);
        when(versiones.registrar(any(), any(), anyString())).thenReturn(1L);
        imagenes = mock(ServicioImagenes.class);
        servicio = new ServicioAutoriaImpl(borradores, basesReglas, basesPublicadas, grafos, publicador,
                publicadorRegla, versiones, imagenes);
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

        assertEquals(List.of("puente_respiracion", "puente_gastro", "puente_cabeza",
                        "puente_actividad", "puente_piel_color", "puente_temperatura"),
                categorias.stream().map(CategoriaDTO::puenteId).toList());
        assertEquals(List.of("Respiración", "Gastrointestinal y urinario", "Cabeza, ojos y oídos",
                        "Actividad y comportamiento", "Piel y color", "Temperatura"),
                categorias.stream().map(CategoriaDTO::nombre).toList());
        assertTrue(categorias.get(0).pregunta().contains("respiración"));
    }

    @Test
    void crearArmaUnBorradorVacioConUnGrafoIdSinTildes() {
        SubgrafoDTO dto = servicio.crear(new SubgrafoRequest("  Fiebre en niños pequeños ", "puente_temperatura",
                " Fiebre o calentura ", "Dra. Prueba", ""), MEDICO_A);

        assertEquals("grafo_fiebre_en_ninos_pequenos", dto.grafoId());
        assertEquals("Fiebre en niños pequeños", dto.nombre());
        assertEquals("Temperatura", dto.categoria());
        assertEquals("Fiebre o calentura", dto.etiquetaOpcion());
        assertEquals(EstadoBorrador.BORRADOR, dto.estado());
        assertTrue(dto.nodos().isEmpty());
        assertEquals(1, dto.avisos().size(), "solo falta la primera pregunta");
        assertNotNull(dto.creadoEn());
    }

    @Test
    void elAutorSaleDeLaCuentaYNoDelRequest() {
        SubgrafoDTO dto = servicio.crear(new SubgrafoRequest("Fiebre", "puente_temperatura", "Fiebre",
                "Otro autor", "Otra institución"), MEDICO_A);

        assertEquals("Dra. A", dto.autor());
        assertEquals("Fundación Canguro", dto.institucion());
        assertEquals(10L, dto.autorId());
    }

    @Test
    void unMedicoListaSoloLoSuyoYElSupervisorTodo() {
        servicio.listar(MEDICO_A);
        verify(borradores).findByAutorId(eq(10L), any(Sort.class));
        verify(borradores, never()).findAll(any(Sort.class));

        servicio.listar(UsuariosPrueba.SUPERVISOR);
        verify(borradores).findAll(any(Sort.class));
    }

    @Test
    void crearSinNombreOConUnaCategoriaQueNoExisteSeRechaza() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(new SubgrafoRequest(" ", "puente_temperatura", "Fiebre", null, null), MEDICO_A));
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(new SubgrafoRequest("Fiebre", "puente_x", "Fiebre", null, null), MEDICO_A));
        verify(borradores, never()).save(any());
    }

    @Test
    void sinNombreParaLaFamiliaSeRechaza() {
        EdicionInvalidaException e = assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(new SubgrafoRequest("Ictericia en la piel", "puente_cabeza", "  ", null, null),
                        MEDICO_A));

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
    void publicarLimpiaLaMetadataDeRevision() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        b.setEstado(EstadoBorrador.EN_REVISION);
        b.setEnviadoRevisionEn(Instant.now());
        b.setRevisorId(30L);
        b.setRevisadoEn(Instant.now());
        b.setComentarioRevision("Corrige la conclusión");

        servicio.publicar(1L);

        assertNull(b.getEnviadoRevisionEn());
        assertNull(b.getRevisorId());
        assertNull(b.getRevisadoEn());
        assertNull(b.getComentarioRevision());
    }

    @Test
    void publicarGuardaUnaCopiaDeLoPublicado() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));

        SubgrafoDTO dto = servicio.publicar(1L);

        assertTrue(dto.puedeDescartar());
        CopiaPublicada copia = b.getCopiaPublicada();
        assertEquals(LienzoFiebre.completo(), copia.documento());
        assertEquals("Fiebre", copia.nombre());
        assertEquals("puente_temperatura", copia.puenteId());
        assertEquals("Fiebre o temperatura alta", copia.etiquetaOpcion());
    }

    @Test
    void publicarPublicaTambienLaBaseDeReglasDelGrafo() {
        guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        BorradorBaseReglaEntity base = BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia());
        base.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        when(basesReglas.findByGrafoId("grafo_fiebre")).thenReturn(Optional.of(base));

        SubgrafoDTO dto = servicio.publicar(1L);

        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());
        verify(publicadorRegla).publicar(eq(base), any(Instant.class));
        assertEquals(EstadoBorrador.PUBLICADO, base.getEstado());
        assertNotNull(base.getPublicadoEn());
        assertNotNull(base.getCopiaPublicada());
        verify(versiones).registrar(any(GrafoClinicoJson.class), any(), anyString());
    }

    @Test
    void publicarConUnaReglaQueApuntaAUnNodoBorradoSeBloquea() {
        guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        when(basesReglas.findByGrafoId("grafo_fiebre"))
                .thenReturn(Optional.of(BaseReglasFiebre.borrador(reglaRota())));

        ContenidoInvalidoException e = assertThrows(ContenidoInvalidoException.class,
                () -> servicio.publicar(1L));

        assertTrue(e.getAvisos().stream()
                .anyMatch(a -> a.codigo() == Aviso.Codigo.REGLA_CONDICION_INVALIDA));
        verify(publicador, never()).publicar(any(), any());
        verify(publicadorRegla, never()).publicar(any(), any());
        verify(versiones, never()).registrar(any(), any(), anyString());
    }

    /** Regla que referencia un nodo que no está en el lienzo completo de fiebre. */
    private static DocumentoBaseRegla reglaRota() {
        return EditorReglas.agregarRegla(DocumentoBaseRegla.vacio(), new EditorReglas.DatosRegla(
                "R rota", null, List.of(new Caso(List.of(new Condicion("pregunta_99", "X")))),
                NivelAlerta.URGENCIA, LienzoFiebre.URGENCIA));
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
        assertEquals("puente_temperatura", dto.puenteId());
        assertEquals("Temperatura", dto.categoria());
        assertEquals("Fiebre o temperatura alta", dto.etiquetaOpcion());
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
    void despublicarDevuelveElBorradorDeReglasABorradorConSuDocumentoIntacto() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);
        DocumentoBaseRegla documento = BaseReglasFiebre.conUrgencia();
        BorradorBaseReglaEntity base = BaseReglasFiebre.borrador(documento);
        base.setEstado(EstadoBorrador.PUBLICADO);
        base.setPublicadoEn(Instant.parse("2026-10-01T00:00:00Z"));
        base.setCopiaPublicada(new CopiaBaseReglaPublicada(base.getDocumento(), base.getNombre(),
                base.getDescripcion(), base.getAutor(), base.getInstitucion()));
        when(basesReglas.findByGrafoId("grafo_fiebre")).thenReturn(Optional.of(base));

        servicio.despublicar(1L);

        assertEquals(EstadoBorrador.BORRADOR, base.getEstado());
        assertNull(base.getPublicadoEn());
        assertNull(base.getCopiaPublicada());
        assertEquals(documento, base.getDocumento(), "el borrador de reglas conserva su documento");
        verify(basesReglas).save(base);
    }

    @Test
    void siElContenidoQueQuedaNoEsValidoElBorradorSigueSiendoPublicado() {
        BorradorSubgrafoEntity b = guardado(LienzoFiebre.borrador(LienzoFiebre.completo()));
        servicio.publicar(1L);
        doThrow(new ContenidoInvalidoException("El contenido que queda no es válido", List.of()))
                .when(publicador).retirar(b);

        assertThrows(ContenidoInvalidoException.class, () -> servicio.despublicar(1L));
        assertEquals(EstadoBorrador.PUBLICADO, b.getEstado());
        assertNotNull(b.getPublicadoEn());
        assertNotNull(b.getCopiaPublicada());
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

    private BorradorSubgrafoEntity conImagenes() {
        DocumentoLienzo documento = EditorLienzo.agregarNodo(DocumentoLienzo.vacio(), null, null,
                new EditorLienzo.DatosNodo(TipoNodo.PREGUNTA, "¿Tiene manchas?", false, null, List.of(),
                        List.of(IMAGEN_1, IMAGEN_2)));
        return guardado(LienzoFiebre.borrador(documento));
    }

    private static NodoRequest nodoConImagenes(List<String> imagenes) {
        return new NodoRequest(null, TipoNodo.PREGUNTA, "¿Tiene manchas?", false, null, List.of(), imagenes);
    }
}
