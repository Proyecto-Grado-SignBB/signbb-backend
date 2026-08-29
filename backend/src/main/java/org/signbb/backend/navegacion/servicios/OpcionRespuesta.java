package org.signbb.backend.navegacion.servicios;

import jakarta.persistence.Embeddable;

@Embeddable
public class OpcionRespuesta {
    private String texto;
    private String valor;

    public OpcionRespuesta() {
    }

    public OpcionRespuesta(String texto, String valor) {
        this.texto = texto;
        this.valor = valor;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }
}
