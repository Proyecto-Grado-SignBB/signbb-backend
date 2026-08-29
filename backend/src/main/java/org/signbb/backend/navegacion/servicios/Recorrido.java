package org.signbb.backend.navegacion.servicios;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Recorrido {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JsonProperty("recorridoId")
    private long id;

    @ManyToOne
    private Nodo nodoActual;
    private String mensaje;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Estado {
        EN_CURSO,
        FINALIZADO
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Resultado {
        NORMAL,
        REQUIERE_ATENCION
    }

    @Enumerated(EnumType.STRING)
    private Estado estado;

    @Enumerated(EnumType.STRING)
    private Resultado resultado;

    public Recorrido() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Nodo getNodoActual() {
        return nodoActual;
    }

    public void setNodoActual(Nodo nodoActual) {
        this.nodoActual = nodoActual;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public Resultado getResultado() {
        return resultado;
    }

    public void setResultado(Resultado resultado) {
        this.resultado = resultado;
    }
}
