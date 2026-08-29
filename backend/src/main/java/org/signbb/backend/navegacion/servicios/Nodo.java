package org.signbb.backend.navegacion.servicios;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Nodo {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String texto;

    @ElementCollection
    @CollectionTable(name = "nodo_opciones", joinColumns = @JoinColumn(name = "nodo_id"))
    @JsonProperty("opciones")
    private List<OpcionRespuesta> opcionesRespuesta;
}
