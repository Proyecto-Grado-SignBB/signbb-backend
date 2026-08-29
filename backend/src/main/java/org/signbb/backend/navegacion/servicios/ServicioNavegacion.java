package org.signbb.backend.navegacion.servicios;

public interface ServicioNavegacion {
    public Nodo guardar(Nodo nodo);

    public Recorrido iniciar(long idGrafo);

    public Recorrido responder(long recorridoId, String respuesta);

    public Recorrido consultar(long recorridoId);
}
