package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_A;

import java.time.Instant;
import java.util.ArrayList;
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
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.CopiaBaseReglaPublicada;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.seguridad.UsuariosPrueba;
import org.springframework.data.domain.Sort;

/** Tests de {@link ServicioAutoriaReglasImpl} con repositorios simulados. */
class ServicioAutoriaReglasImplTest {

    private RepositorioBorradorBaseRegla borradores;
    private RepositorioBorradorSubgrafo subgrafos;
    private PublicadorRegla publicador;
    private ServicioAutoriaReglasImpl servicio;

    @BeforeEach
    void preparar() {
        List<GrafoClinicoEntity> filas = new ArrayList<>();
        filas.add(fila(ContenidoEjemplo.raiz()));
        ContenidoEjemplo.subgrafos().forEach(s -> filas.add(fila(s)));
        RepositorioGrafoClinico grafos = mock(RepositorioGrafoClinico.class);
        when(grafos.findAll()).thenReturn(filas);

        borradores = mock(RepositorioBorradorBaseRegla.class);
        when(borradores.save(any(BorradorBaseReglaEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(borradores.existsByGrafoId(anyString())).thenReturn(false);

        subgrafos = mock(RepositorioBorradorSubgrafo.class);
        when(subgrafos.findByGrafoId(anyString())).thenReturn(Optional.empty());

        publicador = mock(PublicadorRegla.class);
        servicio = new ServicioAutoriaReglasImpl(borradores, subgrafos, grafos, publicador,
                mock(ResolutorCatalogoNodos.class));
    }

    private static GrafoClinicoEntity fila(GrafoClinicoJson documento) {
        GrafoClinicoEntity e = new GrafoClinicoEntity();
        e.setEstructura(documento);
        return e;
    }

    private BorradorBaseReglaEntity guardado(BorradorBaseReglaEntity b) {
        when(borradores.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    private static BaseReglaRequest peticion(String nombre, String grafoId) {
        return new BaseReglaRequest(nombre, "Descripción de prueba", grafoId, "Dra. Prueba", "Hospital");
    }

    @Test
    void crearExigeNombre() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("  ", "grafo_raiz"), MEDICO_A));
    }

    @Test
    void crearExigeUnGrafoValido() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("Reglas", "grafo_inexistente"), MEDICO_A));
    }

    @Test
    void crearCreaElBorradorVacio() {
        when(subgrafos.existsByGrafoId("grafo_fiebre")).thenReturn(true);
        BaseReglaDTO dto = servicio.crear(peticion("Reglas de fiebre", "grafo_fiebre"), MEDICO_A);
        assertEquals("grafo_fiebre", dto.grafoId());
        assertEquals(EstadoBorrador.BORRADOR, dto.estado());
        assertEquals(0, dto.reglas().size());
        assertEquals("Dra. A", dto.autor(), "el autor sale de la cuenta, no del request");
        assertEquals(10L, dto.autorId());
    }

    @Test
    void unMedicoListaSoloSusBasesYElSupervisorTodas() {
        servicio.listar(MEDICO_A);
        verify(borradores).findByAutorId(eq(10L), any(Sort.class));
        verify(borradores, never()).findAll(any(Sort.class));

        servicio.listar(UsuariosPrueba.SUPERVISOR);
        verify(borradores).findAll(any(Sort.class));
    }

    @Test
    void crearRechazaUnaBaseDuplicadaParaElMismoGrafo() {
        when(borradores.existsByGrafoId("grafo_fiebre")).thenReturn(true);
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("Reglas", "grafo_fiebre"), MEDICO_A));
    }

    @Test
    void agregarReglaGeneraElIdYDevuelveElBorrador() {
        BorradorBaseReglaEntity b = guardado(BaseReglasFiebre.borrador(DocumentoBaseRegla.vacio()));

        BaseReglaDTO dto = servicio.agregarRegla(b.getId(),
                new ReglaRequest("Fiebre alta", null,
                        List.of(List.of(new Condicion(LienzoFiebre.INICIO, "Más de 39 °C"))),
                        NivelAlerta.URGENCIA, LienzoFiebre.URGENCIA));

        assertEquals(1, dto.reglas().size());
        assertEquals("R_1", dto.reglas().get(0).id());
    }

    @Test
    void publicarConElLienzoConCambiosSinPublicarSeRechaza() {
        BorradorBaseReglaEntity b = guardado(BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()));
        BorradorSubgrafoEntity lienzo = LienzoFiebre.borrador(LienzoFiebre.completo());
        lienzo.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        when(subgrafos.findByGrafoId("grafo_fiebre")).thenReturn(Optional.of(lienzo));

        assertThrows(EdicionInvalidaException.class, () -> servicio.publicar(b.getId()));
    }

    @Test
    void publicarMarcaElBorradorComoPublicado() {
        BorradorBaseReglaEntity b = guardado(BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()));

        BaseReglaDTO dto = servicio.publicar(b.getId());

        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());
        verify(publicador).publicar(any(BorradorBaseReglaEntity.class), any(Instant.class));
    }

    @Test
    void publicarGuardaLaCopiaParaDescartar() {
        BorradorBaseReglaEntity b = guardado(BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()));

        BaseReglaDTO dto = servicio.publicar(b.getId());

        assertTrue(dto.puedeDescartar(), "tras publicar hay una copia a la que volver");
    }

    @Test
    void descartarCambiosSinPublicarSeRechaza() {
        BorradorBaseReglaEntity b = guardado(BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()));

        assertThrows(EdicionInvalidaException.class, () -> servicio.descartarCambios(b.getId()));
    }

    @Test
    void descartarCambiosVuelveALaCopiaPublicada() {
        BorradorBaseReglaEntity b = BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia());
        b.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        b.setPublicadoEn(Instant.now());
        b.setDocumento(DocumentoBaseRegla.vacio());
        b.setCopiaPublicada(new CopiaBaseReglaPublicada(BaseReglasFiebre.conUrgencia(), "Reglas",
                "descripción", "Dra. Prueba", "Hospital"));
        guardado(b);

        BaseReglaDTO dto = servicio.descartarCambios(b.getId());

        assertEquals(EstadoBorrador.PUBLICADO, dto.estado());
        assertEquals(1, dto.reglas().size());
    }

    @Test
    void eliminarUnaBasePublicadaSeRechaza() {
        BorradorBaseReglaEntity b = BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia());
        b.setPublicadoEn(Instant.now());
        guardado(b);

        assertThrows(EdicionInvalidaException.class, () -> servicio.eliminar(b.getId()));
    }

    @Test
    void losAvisosIncluyenLasReferenciasAlLienzoQueYaNoExisten() {
        DocumentoBaseRegla roto = EditorReglas.agregarRegla(DocumentoBaseRegla.vacio(),
                new EditorReglas.DatosRegla("R rota", null,
                        List.of(new Caso(List.of(new Condicion("pregunta_99", "X")))),
                        NivelAlerta.URGENCIA, LienzoFiebre.URGENCIA));
        BorradorBaseReglaEntity b = guardado(BaseReglasFiebre.borrador(roto));
        when(subgrafos.findByGrafoId("grafo_fiebre"))
                .thenReturn(Optional.of(LienzoFiebre.borrador(LienzoFiebre.completo())));

        List<Aviso> avisos = servicio.avisos(b.getId());

        assertTrue(avisos.stream().anyMatch(a -> a.codigo() == Aviso.Codigo.REGLA_CONDICION_INVALIDA));
    }
}
