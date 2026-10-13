package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.cache.ResolvedorVersiones;
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
    private ResolvedorVersiones versiones;
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
        doAnswer(i -> {
            filas.remove(i.<GrafoClinicoEntity>getArgument(0));
            return null;
        }).when(grafos).delete(any(GrafoClinicoEntity.class));
        RepositorioBaseReglas bases = mock(RepositorioBaseReglas.class);
        List<BaseReglasEntity> reglas = ContenidoEjemplo.bases().stream().map(b -> {
            BaseReglasEntity e = new BaseReglasEntity();
            e.setEstructura(b);
            return e;
        }).toList();
        when(bases.findAll()).thenReturn(reglas);

        resolvedor = mock(ResolvedorContenidoClinico.class);
        versiones = mock(ResolvedorVersiones.class);
        contexto = mock(ContextoClinico.class);
        publicador = new PublicadorSubgrafo(grafos, bases, resolvedor, versiones, contexto);
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
    void publicarEnUnaCategoriaSinSubgrafosLaMuestraALaCuidadora() {
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        borrador.setPuenteId("puente_piel");
        borrador.setCategoria("piel");
        publicador.publicar(borrador, FECHA);

        List<GrafoClinicoJson> subgrafos = filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> !CompositorGrafoClinico.ID_RAIZ.equals(d.graph().id())).toList();
        GrafoClinico tad = GrafoClinicoMapper.desde(CompositorGrafoClinico.componer(
                documento(CompositorGrafoClinico.ID_RAIZ), subgrafos, ContenidoEjemplo.bases()).grafo());

        assertEquals(List.of("piel", "respiracion", "gastrointestinal", "neurologico"),
                tad.obtenerOpciones(tad.obtenerNodoInicial().orElseThrow()).stream()
                        .map(Respuesta::getCondicion).toList());
        Nodo puente = tad.obtenerNodoPorId("puente_piel").orElseThrow();
        assertEquals(List.of(ETIQUETA), tad.obtenerOpciones(puente).stream()
                .map(Respuesta::getCondicion).toList());
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

    @Test
    void cadaPublicacionGuardaUnaVersionConElGrafoCompuesto() {
        publicador.publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);

        ArgumentCaptor<GrafoClinicoJson> grafo = ArgumentCaptor.forClass(GrafoClinicoJson.class);
        ArgumentCaptor<String> origen = ArgumentCaptor.forClass(String.class);
        verify(versiones).registrar(grafo.capture(), any(), origen.capture());
        assertTrue(grafo.getValue().graph().nodes().containsKey("grafo_fiebre/" + LienzoFiebre.INICIO),
                "la versión trae el subgrafo recién publicado");
        assertTrue(grafo.getValue().graph().nodes().containsKey("grafo_respiracion/q_resp_urgencia"),
                "y el resto del contenido");
        assertTrue(origen.getValue().contains("grafo_fiebre"));
    }

    @Test
    void republicarGuardaOtraVersionSinTocarLaAnterior() {
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        publicador.publicar(borrador, FECHA);
        publicador.publicar(borrador, FECHA);

        verify(versiones, times(2)).registrar(any(GrafoClinicoJson.class), any(), any(String.class));
    }

    @Test
    void siNoSePuedePublicarNoSeGuardaVersion() {
        assertThrows(ContenidoInvalidoException.class, () -> publicador
                .publicar(LienzoFiebre.borrador(LienzoFiebre.primeraPregunta()), FECHA));
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        borrador.setGrafoId("grafo/fiebre");
        assertThrows(ContenidoInvalidoException.class, () -> publicador.publicar(borrador, FECHA));

        verify(versiones, never()).registrar(any(), any(), any());
    }

    // ---- Despublicar -------------------------------------------------------

    private BorradorSubgrafoEntity fiebrePublicada() {
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        publicador.publicar(borrador, FECHA);
        return borrador;
    }

    private GrafoClinico contenidoNavegable() {
        List<GrafoClinicoJson> subgrafos = filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> !CompositorGrafoClinico.ID_RAIZ.equals(d.graph().id())).toList();
        return GrafoClinicoMapper.desde(CompositorGrafoClinico.componer(
                documento(CompositorGrafoClinico.ID_RAIZ), subgrafos, ContenidoEjemplo.bases()).grafo());
    }

    @Test
    void retirarQuitaLaFilaLaReferenciaYLaOpcionDeLaPreguntaPuente() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();

        publicador.retirar(borrador);

        assertEquals(4, filas.size(), "raíz + 3 subgrafos del sistema");
        assertTrue(filas.stream().noneMatch(f -> "grafo_fiebre".equals(f.getEstructura().graph().id())));
        GrafoClinicoJson.Grafo raiz = documento(CompositorGrafoClinico.ID_RAIZ).graph();
        assertFalse(raiz.nodes().containsKey(REFERENCIA));
        assertTrue(aristasHaciaLaReferencia().isEmpty());
    }

    @Test
    void despuesDeRetirarLaPreguntaPuenteVuelveATenerSoloSusOpcionesDeAntes() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();

        publicador.retirar(borrador);

        GrafoClinico tad = contenidoNavegable();
        Nodo puente = tad.obtenerNodoPorId("puente_neuro").orElseThrow();
        assertEquals(List.of("iniciar"), tad.obtenerOpciones(puente).stream()
                .map(Respuesta::getCondicion).toList());
        assertTrue(tad.obtenerNodoPorId("grafo_fiebre/" + LienzoFiebre.INICIO).isEmpty());
    }

    @Test
    void retirarNoTocaLosDemasSubgrafos() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();
        List<GrafoClinicoJson> antes = filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> !"grafo_fiebre".equals(d.graph().id())
                        && !CompositorGrafoClinico.ID_RAIZ.equals(d.graph().id()))
                .toList();

        publicador.retirar(borrador);

        List<GrafoClinicoJson> despues = filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> !CompositorGrafoClinico.ID_RAIZ.equals(d.graph().id())).toList();
        assertEquals(antes, despues);
    }

    @Test
    void retirarGuardaUnaVersionNuevaSinElSubgrafo() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();

        publicador.retirar(borrador);

        ArgumentCaptor<GrafoClinicoJson> grafo = ArgumentCaptor.forClass(GrafoClinicoJson.class);
        ArgumentCaptor<String> origen = ArgumentCaptor.forClass(String.class);
        verify(versiones, times(2)).registrar(grafo.capture(), any(), origen.capture());
        assertTrue(origen.getValue().startsWith("Despublicación"));
        assertTrue(origen.getValue().contains("grafo_fiebre"));
        assertFalse(grafo.getValue().graph().nodes().containsKey("grafo_fiebre/" + LienzoFiebre.INICIO));
        assertTrue(grafo.getValue().graph().nodes().containsKey("grafo_respiracion/q_resp_urgencia"));
    }

    @Test
    void despuesDeRetirarInvalidaElCacheYRecargaElContexto() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();
        clearInvocations(grafos, resolvedor, contexto);

        publicador.retirar(borrador);

        InOrder orden = inOrder(grafos, resolvedor, contexto);
        orden.verify(grafos).save(any(GrafoClinicoEntity.class));
        orden.verify(grafos).delete(any(GrafoClinicoEntity.class));
        orden.verify(resolvedor).invalidar();
        orden.verify(contexto).recargar();
    }

    @Test
    void siEsElUnicoSubgrafoPublicadoNoSeRetiraNiSeGuardaNada() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();
        filas.removeIf(f -> !CompositorGrafoClinico.ID_RAIZ.equals(f.getEstructura().graph().id())
                && !"grafo_fiebre".equals(f.getEstructura().graph().id()));
        clearInvocations(grafos, resolvedor, contexto, versiones);

        ContenidoInvalidoException e = assertThrows(ContenidoInvalidoException.class,
                () -> publicador.retirar(borrador));

        assertTrue(e.getMessage().contains("único subgrafo publicado"));
        verify(grafos, never()).save(any());
        verify(grafos, never()).delete(any());
        verify(versiones, never()).registrar(any(), any(), any());
        verify(resolvedor, never()).invalidar();
        verify(contexto, never()).recargar();
    }

    @Test
    void unSubgrafoRetiradoSePuedeVolverAPublicar() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();
        publicador.retirar(borrador);

        publicador.publicar(borrador, FECHA);

        assertEquals(5, filas.size());
        assertEquals(List.of(new GrafoClinicoJson.Arista("puente_neuro", ETIQUETA, REFERENCIA, true)),
                aristasHaciaLaReferencia());
    }

    @Test
    void sinReferenciaQuitaElNodoYSoloLasAristasQueLoTocan() {
        fiebrePublicada();
        GrafoClinicoJson raiz = documento(CompositorGrafoClinico.ID_RAIZ);
        int aristasAntes = raiz.graph().edges().size();

        GrafoClinicoJson sin = PublicadorSubgrafo.sinReferencia(raiz, "grafo_fiebre");

        assertEquals(raiz.graph().nodes().size() - 1, sin.graph().nodes().size());
        assertEquals(aristasAntes - 1, sin.graph().edges().size());
        assertEquals(raiz.graph().id(), sin.graph().id());
        assertEquals(raiz.graph().preguntaInicial(), sin.graph().preguntaInicial());
    }
}
