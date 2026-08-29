package org.signbb.backend.navegacion.servicios;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;

import java.util.List;

@Entity
public class Nodo {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String texto;

    @ElementCollection
    @CollectionTable(name = "nodo_opciones", joinColumns = @JoinColumn(name = "nodo_id"))
    @JsonProperty("opciones")
    private List<OpcionRespuesta> opcionesRespuesta;

    public Nodo() {
    }

    public Nodo(long id, String texto, List<OpcionRespuesta> opcionesRespuesta) {
        this.id = id;
        this.texto = texto;
        this.opcionesRespuesta = opcionesRespuesta;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public List<OpcionRespuesta> getOpcionesRespuesta() {
        return opcionesRespuesta;
    }

    public void setOpcionesRespuesta(List<OpcionRespuesta> opcionesRespuesta) {
        this.opcionesRespuesta = opcionesRespuesta;
    }
}
