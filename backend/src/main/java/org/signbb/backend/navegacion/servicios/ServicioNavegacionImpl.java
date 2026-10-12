package org.signbb.backend.navegacion.servicios;

import java.util.List;
import java.util.UUID;

import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.springframework.stereotype.Service;

/**
 * Implementación del módulo de navegación sobre el motor real: cablea el
 * {@link MotorNavegacion} (recorrido guiado + reglas) con el
 * {@link ContextoClinico} (grafo y reglas en memoria) y el
 * {@link RepositorioSesion} (estado del recorrido entre peticiones).
 *
 * <p>
 * Cada recorrido queda atado a la versión del contenido clínico vigente cuando
 * empieza ({@link ContextoClinico#idVersionActual()}) y la usa hasta terminar,
 * aunque el médico publique cambios mientras tanto.
 *
 * <p>
 * MODIFICADO: se agrega {@link #responder(String, List)} para selección
 * múltiple, con la misma estructura que el {@link #responder(String, String)}
 * ya existente (que no cambia).
 */
@Service
public class ServicioNavegacionImpl implements ServicioNavegacion {

    private final ContextoClinico contexto;
    private final MotorNavegacion motor;
    private final RepositorioSesion repositorioSesion;

    /** Inyecta el contexto clínico, el motor y el repositorio de sesiones. */
    public ServicioNavegacionImpl(ContextoClinico contexto, MotorNavegacion motor,
            RepositorioSesion repositorioSesion) {
        this.contexto = contexto;
        this.motor = motor;
        this.repositorioSesion = repositorioSesion;
    }

    /** Abre una sesión nueva y devuelve la primera pregunta del recorrido. */
    @Override
    public RecorridoDTO iniciar() {
        // El recorrido queda atado a la versión vigente y la termina con ella
        Long version = contexto.idVersionActual();
        GrafoClinico grafo = contexto.grafoDe(version);
        EstadoPaciente estado = new EstadoPaciente(UUID.randomUUID().toString());
        ResultadoPaso paso = motor.iniciar(estado, grafo);
        SesionTriaje sesion = repositorioSesion.crear(estado, paso, version);
        return RecorridoDTO.desde(sesion.getId(), grafo, paso);
    }

    /** Registra la respuesta sobre la pregunta pendiente y avanza un paso. */
    @Override
    public RecorridoDTO responder(String recorridoId, String respuesta) {
        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        GrafoClinico grafo = contexto.grafoDe(sesion.getVersionId());
        ResultadoPaso ultimo = sesion.getUltimoPaso();
        if (ultimo.finalizado()) {
            return RecorridoDTO.desde(recorridoId, grafo, ultimo);
        }

        var nodoActual = ultimo.preguntaActual();
        ResultadoPaso paso = motor.paso(
                grafo, contexto.baseDe(sesion.getVersionId()), sesion.getEstado(), nodoActual, respuesta);
        sesion.setUltimoPaso(paso);
        repositorioSesion.guardar(sesion);
        return RecorridoDTO.desde(recorridoId, grafo, paso);
    }

    /**
     * Registra varias respuestas marcadas a la vez (selección múltiple) y
     * avanza un paso. Con una sola respuesta, delega en
     * {@link #responder(String, String)} para no duplicar el comportamiento
     * ya probado.
     */
    @Override
    public RecorridoDTO responder(String recorridoId, List<String> respuestas) {
        if (respuestas == null || respuestas.isEmpty()) {
            throw new IllegalArgumentException("respuestas no puede ser vacío");
        }
        if (respuestas.size() == 1) {
            return responder(recorridoId, respuestas.get(0));
        }

        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        GrafoClinico grafo = contexto.grafoDe(sesion.getVersionId());
        ResultadoPaso ultimo = sesion.getUltimoPaso();
        if (ultimo.finalizado()) {
            return RecorridoDTO.desde(recorridoId, grafo, ultimo);
        }

        var nodoActual = ultimo.preguntaActual();
        ResultadoPaso paso = motor.paso(
                grafo, contexto.baseDe(sesion.getVersionId()), sesion.getEstado(), nodoActual, respuestas);
        sesion.setUltimoPaso(paso);
        repositorioSesion.guardar(sesion);
        return RecorridoDTO.desde(recorridoId, grafo, paso);
    }

    /**
     * Vuelve a la pregunta anterior y guarda la sesión con el estado ya
     * recalculado, para que el siguiente {@code responder} parta de él.
     */
    @Override
    public RecorridoDTO retroceder(String recorridoId) {
        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        GrafoClinico grafo = contexto.grafoDe(sesion.getVersionId());
        ResultadoPaso paso = motor.retroceder(grafo, contexto.baseDe(sesion.getVersionId()), sesion.getEstado());
        sesion.setUltimoPaso(paso);
        repositorioSesion.guardar(sesion);
        return RecorridoDTO.desde(recorridoId, grafo, paso);
    }

    /** Devuelve el estado actual del recorrido (pregunta pendiente o desenlace). */
    @Override
    public RecorridoDTO consultar(String recorridoId) {
        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        return RecorridoDTO.desde(recorridoId, contexto.grafoDe(sesion.getVersionId()), sesion.getUltimoPaso());
    }
}