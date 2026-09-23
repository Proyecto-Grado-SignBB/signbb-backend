package org.signbb.backend.navegacion.modelo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Almacén en memoria de las sesiones (perfil {@code dev}, sin Docker): el
 * estado del recorrido vive mientras la aplicación esté arriba. Era la
 * implementación única antes del cache; para el perfil {@code docker} ver
 * {@code RepositorioSesionRedis}.
 */
@Component
@Profile("!docker")
public class RepositorioSesionMemoria implements RepositorioSesion {

    private final Map<String, SesionTriaje> sesiones = new ConcurrentHashMap<>();

    @Override
    public SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial) {
        String id = UUID.randomUUID().toString();
        SesionTriaje sesion = new SesionTriaje(id, estado, pasoInicial);
        sesiones.put(id, sesion);
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
}
