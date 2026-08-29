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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
}
