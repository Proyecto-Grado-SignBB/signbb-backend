package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

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
        return new BaseReglaRequest(nombre, grafoId, "Dra. Prueba", "Hospital");
    }

    @Test
    void crearExigeNombre() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("  ", "grafo_raiz")));
    }

    @Test
    void crearExigeUnGrafoValido() {
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("Reglas", "grafo_inexistente")));
    }

    @Test
    void crearCreaElBorradorVacio() {
        when(subgrafos.existsByGrafoId("grafo_fiebre")).thenReturn(true);
        BaseReglaDTO dto = servicio.crear(peticion("Reglas de fiebre", "grafo_fiebre"));
        assertEquals("grafo_fiebre", dto.grafoId());
        assertEquals(EstadoBorrador.BORRADOR, dto.estado());
        assertEquals(0, dto.reglas().size());
    }

    @Test
    void crearRechazaUnaBaseDuplicadaParaElMismoGrafo() {
        when(borradores.existsByGrafoId("grafo_fiebre")).thenReturn(true);
        assertThrows(EdicionInvalidaException.class,
                () -> servicio.crear(peticion("Reglas", "grafo_fiebre")));
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
    void eliminarUnaBasePublicadaSeRechaza() {
        BorradorBaseReglaEntity b = BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia());
        b.setPublicadoEn(Instant.now());
        guardado(b);

        assertThrows(EdicionInvalidaException.class, () -> servicio.eliminar(b.getId()));
    }
}
