package org.signbb.backend.navegacion.servicios;

import java.util.List;
import java.util.UUID;

import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
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
        EstadoPaciente estado = new EstadoPaciente(UUID.randomUUID().toString());
        ResultadoPaso paso = motor.iniciar(estado, contexto.getGrafo());
        SesionTriaje sesion = repositorioSesion.crear(estado, paso);
        return RecorridoDTO.desde(sesion.getId(), contexto.getGrafo(), paso);
    }

    /** Registra la respuesta sobre la pregunta pendiente y avanza un paso. */
    @Override
    public RecorridoDTO responder(String recorridoId, String respuesta) {
        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        ResultadoPaso ultimo = sesion.getUltimoPaso();
        if (ultimo.finalizado()) {
            return RecorridoDTO.desde(recorridoId, contexto.getGrafo(), ultimo);
        }

        var nodoActual = ultimo.preguntaActual();
        ResultadoPaso paso = motor.paso(
                contexto.getGrafo(), contexto.getBase(), sesion.getEstado(), nodoActual, respuesta);
        sesion.setUltimoPaso(paso);
        repositorioSesion.guardar(sesion);
        return RecorridoDTO.desde(recorridoId, contexto.getGrafo(), paso);
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
        ResultadoPaso ultimo = sesion.getUltimoPaso();
        if (ultimo.finalizado()) {
            return RecorridoDTO.desde(recorridoId, contexto.getGrafo(), ultimo);
        }

        var nodoActual = ultimo.preguntaActual();
        ResultadoPaso paso = motor.paso(
                contexto.getGrafo(), contexto.getBase(), sesion.getEstado(), nodoActual, respuestas);
        sesion.setUltimoPaso(paso);
        repositorioSesion.guardar(sesion);
        return RecorridoDTO.desde(recorridoId, contexto.getGrafo(), paso);
    }

    /**
     * Vuelve a la pregunta anterior y guarda la sesión con el estado ya
     * recalculado, para que el siguiente {@code responder} parta de él.
     */
    @Override
    public RecorridoDTO retroceder(String recorridoId) {
        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        ResultadoPaso paso = motor.retroceder(contexto.getGrafo(), contexto.getBase(), sesion.getEstado());
        sesion.setUltimoPaso(paso);
        repositorioSesion.guardar(sesion);
        return RecorridoDTO.desde(recorridoId, contexto.getGrafo(), paso);
    }

    /** Devuelve el estado actual del recorrido (pregunta pendiente o desenlace). */
    @Override
    public RecorridoDTO consultar(String recorridoId) {
        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        return RecorridoDTO.desde(recorridoId, contexto.getGrafo(), sesion.getUltimoPaso());
    }
}