package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.Respuesta;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;

/**
 * Tests de {@link PublicadorSubgrafo} sobre el contenido del sistema
 * ({@link ContenidoEjemplo}) guardado en repositorios simulados: el subgrafo
 * queda guardado y enlazado desde su pregunta puente, el contenido compuesto
 * es navegable, y si algo falla no se guarda nada.
 */
class PublicadorSubgrafoTest {

    private static final Instant FECHA = Instant.parse("2026-10-09T15:00:00Z");
    private static final String REFERENCIA = "ref_grafo_fiebre";
    private static final String ETIQUETA = "Fiebre o temperatura alta";

    private final List<GrafoClinicoEntity> filas = new ArrayList<>();
    private RepositorioGrafoClinico grafos;
    private ResolvedorContenidoClinico resolvedor;
    private ContextoClinico contexto;
    private PublicadorSubgrafo publicador;

    @BeforeEach
    void preparar() {
        filas.add(fila(ContenidoEjemplo.raiz()));
        ContenidoEjemplo.subgrafos().forEach(s -> filas.add(fila(s)));

        grafos = mock(RepositorioGrafoClinico.class);
        when(grafos.findAll()).thenAnswer(i -> new ArrayList<>(filas));
        when(grafos.save(any(GrafoClinicoEntity.class))).thenAnswer(i -> {
            GrafoClinicoEntity e = i.getArgument(0);
            if (filas.stream().noneMatch(f -> f == e)) {
                filas.add(e);
            }
            return e;
        });
        RepositorioBaseReglas bases = mock(RepositorioBaseReglas.class);
        List<BaseReglasEntity> reglas = ContenidoEjemplo.bases().stream().map(b -> {
            BaseReglasEntity e = new BaseReglasEntity();
            e.setEstructura(b);
            return e;
        }).toList();
        when(bases.findAll()).thenReturn(reglas);

        resolvedor = mock(ResolvedorContenidoClinico.class);
        contexto = mock(ContextoClinico.class);
        publicador = new PublicadorSubgrafo(grafos, bases, resolvedor, contexto);
    }

    private static GrafoClinicoEntity fila(GrafoClinicoJson documento) {
        GrafoClinicoEntity e = new GrafoClinicoEntity();
        e.setEstructura(documento);
        return e;
    }

    private GrafoClinicoJson documento(String id) {
        return filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> id.equals(d.graph().id())).findFirst().orElseThrow();
    }

    private List<GrafoClinicoJson.Arista> aristasHaciaLaReferencia() {
        return documento(CompositorGrafoClinico.ID_RAIZ).graph().edges().stream()
                .filter(a -> a.target().equals(REFERENCIA)).toList();
    }

    @Test
    void guardaElSubgrafoYLoEnlazaDesdeSuPreguntaPuente() {
        publicador.publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);

        assertEquals(5, filas.size(), "raíz + 3 subgrafos del sistema + el nuevo");
        assertEquals(LienzoFiebre.INICIO, documento("grafo_fiebre").graph().preguntaInicial());

        GrafoClinicoJson.Grafo raiz = documento(CompositorGrafoClinico.ID_RAIZ).graph();
        GrafoClinicoJson.MetadataNodo ref = raiz.nodes().get(REFERENCIA).metadata();
        assertEquals("referencia", ref.tipo());
        assertEquals("grafo_fiebre", ref.grafoId());
        assertEquals(List.of(new GrafoClinicoJson.Arista("puente_neuro", ETIQUETA, REFERENCIA, true)),
                aristasHaciaLaReferencia());
    }

    @Test
    void laOpcionNuevaLlegaAlSubgrafoJuntoALasQueYaHabia() {
        publicador.publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);

        List<GrafoClinicoJson> subgrafos = filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> !CompositorGrafoClinico.ID_RAIZ.equals(d.graph().id())).toList();
        GrafoClinico tad = GrafoClinicoMapper.desde(CompositorGrafoClinico.componer(
                documento(CompositorGrafoClinico.ID_RAIZ), subgrafos, ContenidoEjemplo.bases()).grafo());

        Nodo puente = tad.obtenerNodoPorId("puente_neuro").orElseThrow();
        List<Respuesta> opciones = tad.obtenerOpciones(puente);
        assertEquals(List.of("iniciar", ETIQUETA), opciones.stream().map(Respuesta::getCondicion).toList());
        assertEquals("grafo_fiebre/" + LienzoFiebre.INICIO, opciones.get(1).getDestino().getId());
    }

    @Test
    void despuesDeGuardarInvalidaElCacheYRecargaElContexto() {
        publicador.publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);

        InOrder orden = inOrder(grafos, resolvedor, contexto);
        orden.verify(grafos, times(2)).save(any(GrafoClinicoEntity.class));
        orden.verify(resolvedor).invalidar();
        orden.verify(contexto).recargar();
    }

    @Test
    void republicarNoDuplicaLaReferenciaYActualizaLaOpcion() {
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        publicador.publicar(borrador, FECHA);
        borrador.setEtiquetaOpcion("Tiene fiebre");

        publicador.publicar(borrador, FECHA);

        assertEquals(5, filas.size());
        assertEquals(List.of(new GrafoClinicoJson.Arista("puente_neuro", "Tiene fiebre", REFERENCIA, true)),
                aristasHaciaLaReferencia());
    }

    @Test
    void conAvisosBloqueantesNoGuardaNada() {
        ContenidoInvalidoException e = assertThrows(ContenidoInvalidoException.class, () -> publicador
                .publicar(LienzoFiebre.borrador(LienzoFiebre.primeraPregunta()), FECHA));

        assertEquals(3, e.getAvisos().size(), "los tres \"+\" pendientes");
        verify(grafos, never()).save(any());
        verify(resolvedor, never()).invalidar();
        verify(contexto, never()).recargar();
    }

    @Test
    void unaOpcionQueYaExisteEnLaPreguntaPuenteSeRechaza() {
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        borrador.setEtiquetaOpcion("Iniciar");

        ContenidoInvalidoException e = assertThrows(ContenidoInvalidoException.class,
                () -> publicador.publicar(borrador, FECHA));

        assertTrue(e.getMessage().contains("ya tiene la opción \"iniciar\""));
        verify(grafos, never()).save(any());
    }

    @Test
    void unaPreguntaPuenteQueNoExisteSeRechaza() {
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        borrador.setPuenteId("puente_inexistente");

        assertThrows(ContenidoInvalidoException.class, () -> publicador.publicar(borrador, FECHA));
        verify(grafos, never()).save(any());
    }

    @Test
    void siLaComposicionFallaNoGuardaNada() {
        // El compositor rechaza un grafoId con '/', el separador de los ids prefijados.
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        borrador.setGrafoId("grafo/fiebre");

        ContenidoInvalidoException e = assertThrows(ContenidoInvalidoException.class,
                () -> publicador.publicar(borrador, FECHA));

        assertTrue(e.getMessage().startsWith("El subgrafo no se pudo publicar"));
        verify(grafos, never()).save(any());
        verify(resolvedor, never()).invalidar();
    }
}
