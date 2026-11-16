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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
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
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

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
    private final List<BaseReglasEntity> baseFilas = new ArrayList<>();
    private RepositorioGrafoClinico grafos;
    private RepositorioBaseReglas bases;
    private RepositorioBorradorBaseRegla basesBorrador;
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
        ContenidoEjemplo.bases().forEach(b -> {
            BaseReglasEntity e = new BaseReglasEntity();
            e.setEstructura(b);
            baseFilas.add(e);
        });
        bases = mock(RepositorioBaseReglas.class);
        when(bases.findAll()).thenAnswer(i -> new ArrayList<>(baseFilas));
        doAnswer(i -> {
            baseFilas.remove(i.<BaseReglasEntity>getArgument(0));
            return null;
        }).when(bases).delete(any(BaseReglasEntity.class));

        resolvedor = mock(ResolvedorContenidoClinico.class);
        versiones = mock(ResolvedorVersiones.class);
        contexto = mock(ContextoClinico.class);
        basesBorrador = mock(RepositorioBorradorBaseRegla.class);
        when(basesBorrador.findByGrafoId(any())).thenReturn(Optional.empty());
        publicador = new PublicadorSubgrafo(grafos, bases, basesBorrador, resolvedor, versiones, contexto);
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

        assertEquals(7, filas.size(), "raíz + 5 subgrafos del sistema + el nuevo");
        assertEquals(LienzoFiebre.INICIO, documento("grafo_fiebre").graph().preguntaInicial());

        GrafoClinicoJson.Grafo raiz = documento(CompositorGrafoClinico.ID_RAIZ).graph();
        GrafoClinicoJson.MetadataNodo ref = raiz.nodes().get(REFERENCIA).metadata();
        assertEquals("referencia", ref.tipo());
        assertEquals("grafo_fiebre", ref.grafoId());
        assertEquals(List.of(new GrafoClinicoJson.Arista("puente_temperatura", ETIQUETA, REFERENCIA, true)),
                aristasHaciaLaReferencia());
    }

    @Test
    void laOpcionNuevaLlegaAlSubgrafo() {
        publicador.publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);

        List<GrafoClinicoJson> subgrafos = filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> !CompositorGrafoClinico.ID_RAIZ.equals(d.graph().id())).toList();
        GrafoClinico tad = GrafoClinicoMapper.desde(CompositorGrafoClinico.componer(
                documento(CompositorGrafoClinico.ID_RAIZ), subgrafos, ContenidoEjemplo.bases()).grafo());

        Nodo puente = tad.obtenerNodoPorId("puente_temperatura").orElseThrow();
        List<Respuesta> opciones = tad.obtenerOpciones(puente);
        assertEquals(List.of(ETIQUETA), opciones.stream().map(Respuesta::getCondicion).toList());
        assertEquals("grafo_fiebre/" + LienzoFiebre.INICIO, opciones.get(0).getDestino().getId());
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

        assertEquals(7, filas.size());
        assertEquals(List.of(new GrafoClinicoJson.Arista("puente_temperatura", "Tiene fiebre", REFERENCIA, true)),
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
    void publicarDevuelveElContenidoCompuesto() {
        CompositorGrafoClinico.Contenido contenido =
                publicador.publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);

        assertTrue(contenido.grafo().graph().nodes().containsKey("grafo_fiebre/" + LienzoFiebre.INICIO),
                "el contenido trae el subgrafo recién publicado");
        assertTrue(contenido.grafo().graph().nodes().containsKey("grafo_respiracion/q_resp_gate_vista"),
                "y el resto del contenido");
        verify(versiones, never()).registrar(any(), any(), any());
    }

    // ---- Despublicar -------------------------------------------------------

    private BorradorSubgrafoEntity fiebrePublicada() {
        BorradorSubgrafoEntity borrador = LienzoFiebre.borrador(LienzoFiebre.completo());
        publicador.publicar(borrador, FECHA);
        return borrador;
    }

    /** Base de reglas publicada de {@code grafo_fiebre}, compilada desde los datos de prueba. */
    private BaseReglasEntity baseDeFiebre() {
        BaseReglasEntity e = new BaseReglasEntity();
        e.setEstructura(CompiladorReglas.compilar(
                BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()), FECHA));
        return e;
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

        assertEquals(6, filas.size(), "raíz + 5 subgrafos del sistema");
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
        assertTrue(tad.obtenerNodoPorId("puente_temperatura").isEmpty(),
                "la categoría sin subgrafos se poda del contenido navegable");
        assertTrue(tad.obtenerNodoPorId("grafo_fiebre/" + LienzoFiebre.INICIO).isEmpty());
    }

    @Test
    void retirarBorraTambienLaBaseDeReglasDelGrafo() {
        BaseReglasEntity baseFiebre = baseDeFiebre();
        baseFilas.add(baseFiebre);
        BorradorSubgrafoEntity borrador = fiebrePublicada();

        publicador.retirar(borrador);

        assertFalse(baseFilas.contains(baseFiebre), "la base del subgrafo retirado se borra");
        assertFalse(baseFilas.stream().anyMatch(e -> "grafo_fiebre".equals(e.getEstructura().grafoId())));
    }

    @Test
    void retirarGuardaUnaVersionNuevaSinElSubgrafo() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();

        publicador.retirar(borrador);

        ArgumentCaptor<GrafoClinicoJson> grafo = ArgumentCaptor.forClass(GrafoClinicoJson.class);
        ArgumentCaptor<String> origen = ArgumentCaptor.forClass(String.class);
        verify(versiones, times(1)).registrar(grafo.capture(), any(), origen.capture());
        assertTrue(origen.getValue().startsWith("Despublicación"));
        assertTrue(origen.getValue().contains("grafo_fiebre"));
        assertFalse(grafo.getValue().graph().nodes().containsKey("grafo_fiebre/" + LienzoFiebre.INICIO));
        assertTrue(grafo.getValue().graph().nodes().containsKey("grafo_respiracion/q_resp_gate_vista"));
    }

    @Test
    void unSubgrafoRetiradoSePuedeVolverAPublicar() {
        BorradorSubgrafoEntity borrador = fiebrePublicada();
        publicador.retirar(borrador);

        publicador.publicar(borrador, FECHA);

        assertEquals(7, filas.size());
        assertEquals(List.of(new GrafoClinicoJson.Arista("puente_temperatura", ETIQUETA, REFERENCIA, true)),
                aristasHaciaLaReferencia());
    }

    // ---- Regla rota por eliminar su nodo destino (PA-027) ------------------

    /**
     * Reproduce el defecto encontrado en las pruebas manuales de PA-027: se publica un
     * subgrafo con una regla, se elimina el nodo al que apuntaba esa regla, se corrige el
     * borrador de la regla para que apunte al nodo nuevo, y se vuelve a publicar. Antes del
     * arreglo, {@code reglas()} validaba contra la base YA publicada (con el destino viejo) en
     * vez del borrador recién corregido, y la publicación fallaba con "nodoDestino ... no
     * existe en el grafo" aunque el lienzo y la regla ya eran consistentes entre sí.
     */
    @Test
    void siSeCorrigeUnaReglaRotaElSubgrafoSePuedeVolverAPublicar() {
        publicador.publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);
        baseFilas.add(baseDeFiebre());

        DocumentoLienzo sinUrgencia = EditorLienzo.eliminarNodo(LienzoFiebre.completo(), LienzoFiebre.URGENCIA);
        DocumentoLienzo corregido = EditorLienzo.agregarNodo(sinUrgencia, LienzoFiebre.INICIO, LienzoFiebre.MAS_39,
                LienzoFiebre.conclusion(Desenlace.URGENCIA, "Vaya al hospital más cercano ahora mismo."));
        String nuevoDestino = corregido.nodos().keySet().stream()
                .filter(id -> !sinUrgencia.nodos().containsKey(id))
                .findFirst().orElseThrow();

        DocumentoBaseRegla reglaCorregida = EditorReglas.agregarRegla(DocumentoBaseRegla.vacio(),
                new EditorReglas.DatosRegla("Fiebre alta", "Más de 39 grados es urgencia.",
                        List.of(new Caso(List.of(new Condicion(LienzoFiebre.INICIO, "Más de 39 °C")))),
                        NivelAlerta.URGENCIA, nuevoDestino));
        when(basesBorrador.findByGrafoId("grafo_fiebre"))
                .thenReturn(Optional.of(BaseReglasFiebre.borrador(reglaCorregida)));

        CompositorGrafoClinico.Contenido contenido =
                publicador.publicar(LienzoFiebre.borrador(corregido), FECHA);

        assertFalse(contenido.grafo().graph().nodes().containsKey("grafo_fiebre/" + LienzoFiebre.URGENCIA),
                "el nodo eliminado ya no está en el grafo publicado");
        assertTrue(contenido.grafo().graph().nodes().containsKey("grafo_fiebre/" + nuevoDestino),
                "el nodo nuevo sí quedó publicado");
    }
}
