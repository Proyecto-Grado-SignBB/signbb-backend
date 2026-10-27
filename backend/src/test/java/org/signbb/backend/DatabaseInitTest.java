package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;

/** Tests de la siembra inicial: solo el grafo raíz, sin subgrafos ni bases. */
class DatabaseInitTest {

    @Test
    void siembraSoloElGrafoRaizCuandoLaBaseEstaVacia() {
        RepositorioGrafoClinico grafos = mock(RepositorioGrafoClinico.class);
        ResolvedorContenidoClinico resolvedor = mock(ResolvedorContenidoClinico.class);
        when(grafos.findAll()).thenReturn(List.of());

        new DatabaseInit(grafos, resolvedor).run();

        ArgumentCaptor<GrafoClinicoEntity> guardado = ArgumentCaptor.forClass(GrafoClinicoEntity.class);
        verify(grafos).save(guardado.capture());
        assertEquals(CompositorGrafoClinico.ID_RAIZ, guardado.getValue().getEstructura().graph().id());
        verify(resolvedor).invalidar();
    }

    @Test
    void siYaHayRaizNoVuelveASembrar() {
        RepositorioGrafoClinico grafos = mock(RepositorioGrafoClinico.class);
        ResolvedorContenidoClinico resolvedor = mock(ResolvedorContenidoClinico.class);
        GrafoClinicoEntity raiz = new GrafoClinicoEntity();
        raiz.setEstructura(ContenidoEjemplo.raiz());
        when(grafos.findAll()).thenReturn(List.of(raiz));

        new DatabaseInit(grafos, resolvedor).run();

        verify(grafos, never()).save(any());
        verify(resolvedor, never()).invalidar();
    }
}
