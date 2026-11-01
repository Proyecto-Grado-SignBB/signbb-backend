package org.signbb.backend.navegacion.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.VersionesDePrueba;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Un recorrido se ata a la versión del contenido con la que empezó: si el
 * médico publica mientras la cuidadora responde, ella termina con lo que
 * estaba viendo y los recorridos nuevos ya usan la versión nueva.
 */
class ServicioNavegacionVersionesTest {

    private final AtomicLong vigente = new AtomicLong(1);
    private final Map<String, SesionTriaje> sesiones = new ConcurrentHashMap<>();
    private ContextoClinico contexto;
    private ServicioNavegacionImpl servicio;

    @BeforeEach
    void preparar() {
        CompositorGrafoClinico.Contenido compuesto = ContenidoEjemplo.compuesto();
        ResolvedorVersiones versiones = mock(ResolvedorVersiones.class);
        when(versiones.idActual()).thenAnswer(i -> vigente.get());
        when(versiones.documentoGrafo(1L)).thenReturn(compuesto.grafo());
        when(versiones.documentoGrafo(2L)).thenReturn(VersionesDePrueba.conOpcionExtra(compuesto.grafo()));
        when(versiones.documentoReglas(1L)).thenReturn(compuesto.reglas());
        when(versiones.documentoReglas(2L)).thenReturn(compuesto.reglas());
        contexto = new ContextoClinico(versiones);

        RepositorioSesion repositorio = new RepositorioSesion() {
            @Override
            public SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial, Long versionId) {
                SesionTriaje sesion = new SesionTriaje(UUID.randomUUID().toString(), estado, pasoInicial, versionId);
                sesiones.put(sesion.getId(), sesion);
                return sesion;
            }

            @Override
            public SesionTriaje buscar(String id) {
                SesionTriaje sesion = sesiones.get(id);
                if (sesion == null) {
                    throw new RecursoNoEncontradoException("Recorrido no encontrado: " + id);
                }
                return sesion;
            }

            @Override
            public void guardar(SesionTriaje sesion) {
                sesiones.put(sesion.getId(), sesion);
            }

            @Override
            public boolean existe(String id) {
                return sesiones.containsKey(id);
            }

            @Override
            public void eliminar(String id) {
                sesiones.remove(id);
            }
        };
        servicio = new ServicioNavegacionImpl(contexto, new MotorNavegacion(new ServicioReglasImpl()), repositorio);
    }

    /** El médico publica: la versión 2 pasa a ser la vigente. */
    private void publicarVersionNueva() {
        vigente.set(2);
        contexto.recargar();
    }

    @Test
    void elRecorridoNuevoQuedaAtadoALaVersionVigente() {
        String id = servicio.iniciar().recorridoId();

        assertEquals(1L, sesiones.get(id).getVersionId());
    }

    @Test
    void unRecorridoEnCursoSigueConSuVersionAunqueSePubliqueOtra() {
        String viejo = servicio.iniciar().recorridoId();

        publicarVersionNueva();

        RecorridoDTO enCurso = servicio.consultar(viejo);
        assertFalse(enCurso.pregunta().opciones().contains(VersionesDePrueba.OPCION_NUEVA));
        // y responder tampoco lo pasa a la versión nueva
        servicio.responder(viejo, "respiracion");
        assertEquals(1L, sesiones.get(viejo).getVersionId());
    }

    @Test
    void losRecorridosNuevosUsanLaVersionNueva() {
        String viejo = servicio.iniciar().recorridoId();
        publicarVersionNueva();

        RecorridoDTO nuevo = servicio.iniciar();

        assertTrue(nuevo.pregunta().opciones().contains(VersionesDePrueba.OPCION_NUEVA));
        assertEquals(2L, sesiones.get(nuevo.recorridoId()).getVersionId());
        assertEquals(1L, sesiones.get(viejo).getVersionId());
    }
}
