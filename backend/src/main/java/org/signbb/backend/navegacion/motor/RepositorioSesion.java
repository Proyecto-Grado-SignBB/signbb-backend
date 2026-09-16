package org.signbb.backend.navegacion.motor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.model.EstadoPaciente;
import org.springframework.stereotype.Component;

/**
 * Almacén en memoria de las sesiones de triaje en curso.
 *
 * <p>Guarda cada {@link SesionTriaje} indexada por su id en un
 * {@link ConcurrentHashMap}. Es deliberadamente simple (no JPA): el estado del
 * recorrido vive mientras la aplicación esté arriba, suficiente para el flujo
 * de una consulta guiada.
 */
@Component
public class RepositorioSesion {

    private final Map<String, SesionTriaje> sesiones = new ConcurrentHashMap<>();

    /** Crea y almacena una sesión nueva con un id generado. */
    public SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial) {
        String id = UUID.randomUUID().toString();
        SesionTriaje sesion = new SesionTriaje(id, estado, pasoInicial);
        sesiones.put(id, sesion);
        return sesion;
    }

    /** Busca una sesión por id; falla con 404 si no existe. */
    public SesionTriaje buscar(String id) {
        SesionTriaje sesion = sesiones.get(id);
        if (sesion == null) {
            throw new RecursoNoEncontradoException("Recorrido no encontrado: " + id);
        }
        return sesion;
    }

    /** Persiste el estado actualizado de una sesión. */
    public void guardar(SesionTriaje sesion) {
        sesiones.put(sesion.getId(), sesion);
    }
}
