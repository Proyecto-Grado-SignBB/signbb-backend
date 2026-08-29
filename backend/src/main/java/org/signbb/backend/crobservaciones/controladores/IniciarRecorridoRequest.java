package org.signbb.backend.crobservaciones.controladores;

public class IniciarRecorridoRequest {
    private long grafoClinicoId;

    public IniciarRecorridoRequest() {
    }

    public IniciarRecorridoRequest(long grafoClinicoId) {
        this.grafoClinicoId = grafoClinicoId;
    }

    public long getGrafoClinicoId() {
        return grafoClinicoId;
    }

    public void setGrafoClinicoId(long grafoClinicoId) {
        this.grafoClinicoId = grafoClinicoId;
    }
}
