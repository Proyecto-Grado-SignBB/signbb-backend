package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;

/**
 * Tests de {@link PublicadorRegla} sobre el contenido del sistema guardado en
 * repositorios simulados: la base queda guardada y compuesta con el subgrafo
 * publicado, y si algo falla no se guarda nada.
 */
class PublicadorReglaTest {

    private static final Instant FECHA = Instant.parse("2026-10-11T15:00:00Z");

    private final List<GrafoClinicoEntity> filas = new ArrayList<>();
    private final List<BaseReglasEntity> baseFilas = new ArrayList<>();
    private RepositorioBaseReglas bases;
    private ResolvedorContenidoClinico resolvedor;
    private ResolvedorVersiones versiones;
    private ContextoClinico contexto;
    private PublicadorRegla publicador;

    @BeforeEach
    void preparar() {
        filas.add(fila(ContenidoEjemplo.raiz()));
        ContenidoEjemplo.subgrafos().forEach(s -> filas.add(fila(s)));

        RepositorioGrafoClinico grafos = mock(RepositorioGrafoClinico.class);
        when(grafos.findAll()).thenAnswer(i -> new ArrayList<>(filas));
        when(grafos.save(any(GrafoClinicoEntity.class))).thenAnswer(i -> {
            GrafoClinicoEntity e = i.getArgument(0);
            if (filas.stream().noneMatch(f -> f == e)) {
                filas.add(e);
            }
            return e;
        });

        ContenidoEjemplo.bases().forEach(b -> {
            BaseReglasEntity e = new BaseReglasEntity();
            e.setEstructura(b);
            baseFilas.add(e);
        });
        bases = mock(RepositorioBaseReglas.class);
        when(bases.findAll()).thenAnswer(i -> new ArrayList<>(baseFilas));
        when(bases.save(any(BaseReglasEntity.class))).thenAnswer(i -> {
            BaseReglasEntity e = i.getArgument(0);
            if (baseFilas.stream().noneMatch(f -> f == e)) {
                baseFilas.add(e);
            }
            return e;
        });

        resolvedor = mock(ResolvedorContenidoClinico.class);
        contexto = mock(ContextoClinico.class);
        versiones = mock(ResolvedorVersiones.class);

        // Publica el subgrafo de fiebre para que su grafo exista y la raíz lo referencie.
        new PublicadorSubgrafo(grafos, bases, resolvedor, mock(ResolvedorVersiones.class), contexto)
                .publicar(LienzoFiebre.borrador(LienzoFiebre.completo()), FECHA);
        clearInvocations(resolvedor, contexto);

        publicador = new PublicadorRegla(grafos, bases, resolvedor, versiones, contexto);
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

    private BaseReglas composicion() {
        List<GrafoClinicoJson> subgrafos = filas.stream().map(GrafoClinicoEntity::getEstructura)
                .filter(d -> !CompositorGrafoClinico.ID_RAIZ.equals(d.graph().id())).toList();
        List<BaseReglasJson> reglas = baseFilas.stream().map(BaseReglasEntity::getEstructura)
                .filter(b -> b.grafoId() != null && !b.grafoId().isBlank()).toList();
        CompositorGrafoClinico.Contenido contenido =
                CompositorGrafoClinico.componer(documento(CompositorGrafoClinico.ID_RAIZ), subgrafos, reglas);
        return BaseReglasMapper.desde(contenido.reglas(), GrafoClinicoMapper.desde(contenido.grafo()));
    }

    @Test
    void guardaLaBaseYLaDejaActivaEnLaComposicion() {
        publicador.publicar(BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()), FECHA);

        BaseReglas tad = composicion();
        assertTrue(tad.contains("grafo_fiebre/R_1"), "la regla compuesta con el prefijo del grafo");
        assertEquals("grafo_fiebre/conclusion_5",
                tad.obtenerRegla("grafo_fiebre/R_1").getNodoDestino().getId());
    }

    @Test
    void despuesDeGuardarInvalidaElCacheYRecargaElContexto() {
        publicador.publicar(BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()), FECHA);

        InOrder orden = inOrder(bases, resolvedor, contexto);
        orden.verify(bases).save(any(BaseReglasEntity.class));
        orden.verify(resolvedor).invalidar();
        orden.verify(contexto).recargar();
    }

    @Test
    void republicarNoDuplicaLaBase() {
        BorradorBaseReglaEntity borrador = BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia());
        publicador.publicar(borrador, FECHA);
        publicador.publicar(borrador, FECHA);

        long fiebre = baseFilas.stream().map(BaseReglasEntity::getEstructura)
                .filter(b -> "grafo_fiebre".equals(b.grafoId())).count();
        assertEquals(1, fiebre);
    }

    @Test
    void conAvisosBloqueantesNoGuardaNada() {
        BorradorBaseReglaEntity borrador = BaseReglasFiebre.borrador(BaseReglasFiebre.incompleta());

        ContenidoInvalidoException e = assertThrows(ContenidoInvalidoException.class,
                () -> publicador.publicar(borrador, FECHA));

        assertEquals(4, e.getAvisos().size(), "sin nombre, sin condiciones, sin destino y nivel NORMAL");
        verify(bases, never()).save(any());
        verify(resolvedor, never()).invalidar();
    }

    @Test
    void publicarRegistraUnaVersionConGrafoYReglas() {
        publicador.publicar(BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia()), FECHA);

        verify(versiones).registrar(any(GrafoClinicoJson.class), any(BaseReglasJson.class), anyString());
    }

    @Test
    void unGrafoNoPublicadoSeRechaza() {
        BorradorBaseReglaEntity borrador = BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia());
        borrador.setGrafoId("grafo_inexistente");

        assertThrows(ContenidoInvalidoException.class, () -> publicador.publicar(borrador, FECHA));
        verify(bases, never()).save(any());
    }

    @Test
    void laBaseDelGrafoRaizNoSePublica() {
        BorradorBaseReglaEntity borrador = BaseReglasFiebre.borrador(BaseReglasFiebre.conUrgencia());
        borrador.setGrafoId("grafo_raiz");

        assertThrows(EdicionInvalidaException.class, () -> publicador.publicar(borrador, FECHA));
        verify(bases, never()).save(any());
    }
}
