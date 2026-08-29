package org.signbb.backend.crobservaciones.controladores;

public class RespuestaRequest {
    private String respuesta;

    public RespuestaRequest() {
    }

    public RespuestaRequest(String respuesta) {
        this.respuesta = respuesta;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }
}
