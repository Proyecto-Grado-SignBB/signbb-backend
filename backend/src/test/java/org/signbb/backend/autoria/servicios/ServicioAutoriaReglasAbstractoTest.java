package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_A;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.BaseReglaDTO;
import org.signbb.backend.autoria.controladores.BaseReglaRequest;
import org.signbb.backend.autoria.controladores.ReglaRequest;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

/**
 * CRUD y publicación de bases de reglas con datos abstractos
 * ({@code resp1/op1/op2/op3}) y todos los colaboradores simulados con Mockito:
 * no depende del dominio, de la base de datos ni de Redis.
 */
class ServicioAutoriaReglasAbstractoTest {

    private static final String RESP = "resp1";

    private RepositorioBorradorBaseRegla borradores;
    private RepositorioBorradorSubgrafo subgrafos;
    private PublicadorRegla publicador;
    private ServicioAutoriaReglasImpl servicio;

    @BeforeEach
    void preparar() {
        borradores = mock(RepositorioBorradorBaseRegla.class);
        when(borradores.save(any(BorradorBaseReglaEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(borradores.existsByGrafoId(anyString())).thenReturn(false);

        subgrafos = mock(RepositorioBorradorSubgrafo.class);
        when(subgrafos.findByGrafoId(anyString())).thenReturn(Optional.empty());

        RepositorioGrafoClinico grafos = mock(RepositorioGrafoClinico.class);
        when(grafos.findAll()).thenReturn(List.of());

        publicador = mock(PublicadorRegla.class);
        servicio = new ServicioAutoriaReglasImpl(borradores, subgrafos, grafos, publicador,
                mock(ResolutorCatalogoNodos.class));
    }

    private static BaseReglaRequest peticion(String nombre, String grafoId) {
        return new BaseReglaRequest(nombre, "descripción de prueba", grafoId, "Dra. Prueba", "Hospital");
    }

    /** Una regla abstracta; cada caso es una lista de condiciones (AND). */
    private static ReglaRequest regla(List<List<Condicion>> casos) {
        return new ReglaRequest("Regla abstracta", null, casos, NivelAlerta.URGENCIA, "dest");
    }

    private static BorradorBaseReglaEntity borrador(String grafoId, DocumentoBaseRegla documento) {
        BorradorBaseReglaEntity b = new BorradorBaseReglaEntity();
        b.setId(1L);
        b.setNombre("Base abstracta");
        b.setGrafoId(grafoId);
        b.setDocumento(documento);
        b.setEstado(EstadoBorrador.BORRADOR);
        return b;
    }

    private BorradorBaseReglaEntity guardado(BorradorBaseReglaEntity b) {
        when(borradores.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void crearBaseParaElGrafoRaizSeRechaza() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("Base raíz", "grafo_raiz"), MEDICO_A));
    }

    @Test
    void crearBaseParaUnSubgrafoExistente() {
        when(subgrafos.existsByGrafoId("grafo_x")).thenReturn(true);

        BaseReglaDTO dto = servicio.crear(peticion("Base x", "grafo_x"), MEDICO_A);

        assertEquals("grafo_x", dto.grafoId());
    }

    @Test
    void crearRechazaUnGrafoDesconocido() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("Base", "grafo_zzz"), MEDICO_A));
    }

    @Test
    void crearRechazaUnaBaseDuplicada() {
        when(subgrafos.existsByGrafoId("grafo_x")).thenReturn(true);
        when(borradores.existsByGrafoId("grafo_x")).thenReturn(true);

        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("Base", "grafo_x"), MEDICO_A));
    }

    @Test
    void agregarReglaGeneraElIdConCondicionesAbstractas() {
        BorradorBaseReglaEntity b = guardado(borrador("grafo_x", DocumentoBaseRegla.vacio()));

        BaseReglaDTO dto = servicio.agregarRegla(b.getId(), regla(List.of(
                List.of(new Condicion(RESP, "op1"), new Condicion(RESP, "op2"),
                        new Condicion(RESP, "op3")))));

        assertEquals(1, dto.reglas().size());
        assertEquals("R_1", dto.reglas().get(0).id());
    }

    @Test
    void editarReglaConservaElId() {
        BorradorBaseReglaEntity b = guardado(borrador("grafo_x", DocumentoBaseRegla.vacio()));
        servicio.agregarRegla(b.getId(), regla(List.of(List.of(new Condicion(RESP, "op1")))));

        BaseReglaDTO dto = servicio.editarRegla(b.getId(), "R_1",
                regla(List.of(List.of(new Condicion(RESP, "op3")))));

        assertEquals(1, dto.reglas().size());
        assertEquals("R_1", dto.reglas().get(0).id());
        assertEquals(new Condicion(RESP, "op3"), dto.reglas().get(0).casos().get(0).get(0));
    }

    @Test
    void eliminarReglaDejaLaBaseVacia() {
        BorradorBaseReglaEntity b = guardado(borrador("grafo_x", DocumentoBaseRegla.vacio()));
        servicio.agregarRegla(b.getId(), regla(List.of(List.of(new Condicion(RESP, "op1")))));

        BaseReglaDTO dto = servicio.eliminarRegla(b.getId(), "R_1");

        assertEquals(0, dto.reglas().size());
    }

    @Test
    void eliminarUnaBasePublicadaSeRechaza() {
        BorradorBaseReglaEntity b = borrador("grafo_x", DocumentoBaseRegla.vacio());
        b.setPublicadoEn(Instant.now());
        guardado(b);

        assertThrows(EdicionInvalidaException.class, () -> servicio.eliminar(b.getId()));
    }

    @Test
    void publicarExigeQueElLienzoEstePublicado() {
        BorradorBaseReglaEntity b = guardado(borrador("grafo_x", DocumentoBaseRegla.vacio()));
        BorradorSubgrafoEntity lienzo = new BorradorSubgrafoEntity();
        lienzo.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        when(subgrafos.findByGrafoId("grafo_x")).thenReturn(Optional.of(lienzo));

        assertThrows(EdicionInvalidaException.class, () -> servicio.publicar(b.getId()));
    }

    @Test
    void publicarDelegaEnElPublicadorYQuedaPublicado() {
        BorradorBaseReglaEntity b = guardado(borrador("grafo_x", DocumentoBaseRegla.vacio()));

        BaseReglaDTO dto = servicio.publicar(b.getId());

        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());
        verify(publicador).publicar(any(BorradorBaseReglaEntity.class), any(Instant.class));
    }

    @Test
    void editarTrasPublicarMarcaCambiosSinPublicar() {
        BorradorBaseReglaEntity b = borrador("grafo_x", DocumentoBaseRegla.vacio());
        b.setEstado(EstadoBorrador.PUBLICADO);
        guardado(b);

        BaseReglaDTO dto = servicio.actualizar(b.getId(), peticion("Nuevo nombre", "grafo_x"));

        assertEquals(EstadoBorrador.CAMBIOS_SIN_PUBLICAR, dto.estado());
    }

    @Test
    void laBaseRaizNoSeLeeNiSeConsultaSuCatalogo() {
        BorradorBaseReglaEntity b = guardado(borrador("grafo_raiz", DocumentoBaseRegla.vacio()));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(b.getId()));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.avisos(b.getId()));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.catalogo("grafo_raiz"));
    }

    @Test
    void lasOperacionesDeEscrituraSobreLaBaseRaizSeRechazan() {
        BorradorBaseReglaEntity b = guardado(borrador("grafo_raiz", DocumentoBaseRegla.vacio()));
        var datosRegla = regla(List.of(List.of(new Condicion(RESP, "op1"))));

        assertThrows(EdicionInvalidaException.class,
                () -> servicio.actualizar(b.getId(), peticion("Nuevo nombre", "grafo_raiz")));
        assertThrows(EdicionInvalidaException.class, () -> servicio.agregarRegla(b.getId(), datosRegla));
        assertThrows(EdicionInvalidaException.class, () -> servicio.editarRegla(b.getId(), "R_1", datosRegla));
        assertThrows(EdicionInvalidaException.class, () -> servicio.eliminarRegla(b.getId(), "R_1"));
        assertThrows(EdicionInvalidaException.class, () -> servicio.publicar(b.getId()));
        assertThrows(EdicionInvalidaException.class, () -> servicio.descartarCambios(b.getId()));
        assertThrows(EdicionInvalidaException.class, () -> servicio.eliminar(b.getId()));
    }
}
