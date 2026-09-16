package org.signbb.backend.navegacion.servicios;

import java.util.UUID;

import org.signbb.backend.crobservaciones.controladores.RecorridoDTO;
import org.signbb.backend.model.EstadoPaciente;
import org.signbb.backend.navegacion.motor.ContextoClinico;
import org.signbb.backend.navegacion.motor.MotorNavegacion;
import org.signbb.backend.navegacion.motor.RepositorioSesion;
import org.signbb.backend.navegacion.motor.ResultadoPaso;
import org.signbb.backend.navegacion.motor.SesionTriaje;
import org.springframework.stereotype.Service;

/**
 * Implementación del módulo de navegación sobre el motor real: cablea el
 * {@link MotorNavegacion} (recorrido guiado + reglas) con el
 * {@link ContextoClinico} (grafo y reglas en memoria) y el
 * {@link RepositorioSesion} (estado del recorrido entre peticiones).
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

    /** Devuelve el estado actual del recorrido (pregunta pendiente o desenlace). */
    @Override
    public RecorridoDTO consultar(String recorridoId) {
        SesionTriaje sesion = repositorioSesion.buscar(recorridoId);
        return RecorridoDTO.desde(recorridoId, contexto.getGrafo(), sesion.getUltimoPaso());
    }
}
