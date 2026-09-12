package org.signbb.backend.navegacion.servicios;

/** API del módulo de navegación por el grafo clínico (en construcción). */
public interface ServicioNavegacion {

    /** Persiste un nodo. */
    public Nodo guardar(Nodo nodo);

    /** Inicia un recorrido en el grafo indicado. */
    public Recorrido iniciar(long idGrafo);

    /** Registra la respuesta y avanza el recorrido. */
    public Recorrido responder(long recorridoId, String respuesta);

    /** Consulta el estado actual de un recorrido. */
    public Recorrido consultar(long recorridoId);
}
