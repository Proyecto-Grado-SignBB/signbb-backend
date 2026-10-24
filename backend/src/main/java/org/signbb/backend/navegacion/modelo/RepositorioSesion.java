package org.signbb.backend.navegacion.modelo;

/**
 * Almacén de las sesiones de triaje en curso.
 *
 * <p>
 * MODIFICADO (cache): pasa a interfaz cuya implementación es
 * {@code RepositorioSesionRedis} (perfil {@code docker}), según el documento
 * de diseño: las sesiones temporales de los padres viven en la caché
 * distribuida para que las consultas sobrevivan reinicios del backend.
 *
 * <p>
 * MODIFICADO (CU10/CU11): se agregan {@link #existe(String)}, para saber si
 * una sesión ya venció sin tratarlo como error, y {@link #eliminar(String)},
 * para borrar la sesión de un recorrido que se reinicia.
 */
public interface RepositorioSesion {

    /**
     * Crea y almacena una sesión nueva con un id generado, atada a la versión
     * del contenido clínico con la que empieza el recorrido.
     */
    SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial, Long versionId);

    /** Crea una sesión sin versión: usa siempre la vigente (para pruebas y sesiones antiguas). */
    default SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial) {
        return crear(estado, pasoInicial, null);
    }

    /** Busca una sesión por id; falla con 404 si no existe. */
    SesionTriaje buscar(String id);

    /** Persiste el estado actualizado de una sesión. */
    void guardar(SesionTriaje sesion);

    /** Indica si la sesión sigue guardada; {@code false} si venció o nunca existió. */
    boolean existe(String id);

    /** Borra la sesión; si ya no existe, no hace nada. */
    void eliminar(String id);
}
