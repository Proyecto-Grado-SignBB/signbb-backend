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

/**
 * Entidad JPA de nodo de pregunta persistido para el módulo de navegación;
 * distinta del TAD {@link org.signbb.backend.navegacion.modelo.Nodo}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Nodo {
    /** Identificador autogenerado. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    /** Texto de la pregunta mostrada al cuidador. */
    private String texto;

    /** Opciones de respuesta del nodo. */
    @ElementCollection
    @CollectionTable(name = "nodo_opciones", joinColumns = @JoinColumn(name = "nodo_id"))
    @JsonProperty("opciones")
    private List<OpcionRespuesta> opcionesRespuesta;
}
