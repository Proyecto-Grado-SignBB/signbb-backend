package org.signbb.backend.navegacion.servicios;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA de grafo clínico persistido para el módulo de navegación;
 * distinta del TAD {@link org.signbb.backend.model.GrafoClinico}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Grafo {
    /** Identificador autogenerado. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    /** Nombre del grafo. */
    private String nombre;

    /** Nodo raíz por donde inicia el recorrido. */
    @ManyToOne
    private Nodo nodoRaiz;
}
