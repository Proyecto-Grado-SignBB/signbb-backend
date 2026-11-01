package org.signbb.backend.navegacion.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

/** Tests de {@link ContextoClinico}: la versión vigente, las versiones por id y la recarga. */
class ContextoClinicoTest {

    private ResolvedorVersiones versiones;
    private ContextoClinico contexto;
    private GrafoClinicoJson version1;
    private GrafoClinicoJson version2;

    @BeforeEach
    void preparar() {
        CompositorGrafoClinico.Contenido compuesto = ContenidoEjemplo.compuesto();
        version1 = compuesto.grafo();
        version2 = VersionesDePrueba.conOpcionExtra(version1);

        versiones = mock(ResolvedorVersiones.class);
        when(versiones.idActual()).thenReturn(1L);
        when(versiones.documentoGrafo(1L)).thenReturn(version1);
        when(versiones.documentoGrafo(2L)).thenReturn(version2);
        when(versiones.documentoReglas(1L)).thenReturn(compuesto.reglas());
        when(versiones.documentoReglas(2L)).thenReturn(compuesto.reglas());
        contexto = new ContextoClinico(versiones);
    }

    private static int opcionesIniciales(GrafoClinico grafo) {
        return grafo.obtenerOpciones(grafo.obtenerNodoPorId("pregunta_inicial").orElseThrow()).size();
    }

    @Test
    void getGrafoYGetBaseSonLosDeLaVersionVigente() {
        assertEquals(1L, contexto.idVersionActual());
        assertEquals(opcionesIniciales(contexto.grafoDe(1L)), opcionesIniciales(contexto.getGrafo()));
        assertEquals(contexto.baseDe(1L).size(), contexto.getBase().size());
    }

    @Test
    void cadaVersionTieneSuPropioGrafo() {
        int enLaUno = opcionesIniciales(contexto.grafoDe(1L));
        int enLaDos = opcionesIniciales(contexto.grafoDe(2L));

        assertEquals(enLaUno + 1, enLaDos);
    }

    @Test
    void olvidarSacaDeMemoriaSoloLasVersionesIndicadas() {
        contexto.grafoDe(1L);
        contexto.grafoDe(2L);

        contexto.olvidar(List.of(2L));
        contexto.grafoDe(1L);
        contexto.grafoDe(2L);

        verify(versiones, times(1)).documentoGrafo(1L);
        verify(versiones, times(2)).documentoGrafo(2L);
    }

    @Test
    void despuesDeRecargarLosRecorridosNuevosVenLaVersionNueva() {
        GrafoClinico antes = contexto.getGrafo();
        when(versiones.idActual()).thenReturn(2L);

        assertSame(antes, contexto.getGrafo(), "sin recargar se sigue sirviendo lo que había en memoria");
        contexto.recargar();

        assertEquals(2L, contexto.idVersionActual());
        assertNotSame(antes, contexto.getGrafo());
        assertEquals(opcionesIniciales(antes) + 1, opcionesIniciales(contexto.getGrafo()));
        // un recorrido que empezó con la versión 1 sigue viéndola
        assertEquals(opcionesIniciales(antes), opcionesIniciales(contexto.grafoDe(1L)));
    }
}
