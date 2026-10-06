package org.signbb.backend.navegacion.modelo;

import java.util.List;

/**
 * Una opción que el cuidador marcó en una pregunta de selección múltiple pero
 * que el recorrido todavía no ha seguido. Se guarda en la cola de opciones
 * pendientes de {@link EstadoPaciente} para retomarla cuando la rama que se
 * está siguiendo termine en una recomendación.
 *
 * <p>
 * La gravedad no se guarda: se calcula al ordenar, a partir del nodo destino
 * (ver {@code MotorNavegacion.ordenCola}). Lo que sí se guarda es
 * {@code orden}, el número de llegada, que desempata a igual gravedad (gana la
 * más antigua) y que {@code PriorityQueue} de Java no garantiza por sí sola.
 *
 * @param idOrigen  pregunta en la que se marcó la opción.
 * @param condicion texto de la opción marcada.
 * @param idDestino nodo al que lleva la opción.
 * @param orden     número de llegada a la cola (menor = más antigua).
 * @param camino    recorrido real hasta el destino, incluido el destino, para
 *                  retomarlo como camino causal si esta opción se sigue.
 */
public record OpcionPendiente(String idOrigen, String condicion, String idDestino, int orden,
        List<PasoRuta> camino) {

    /** Valida los ids y guarda una copia inmutable del camino. */
    public OpcionPendiente {
        if (idOrigen == null || idOrigen.isEmpty()) {
            throw new IllegalArgumentException("idOrigen debe ser una cadena no vacía");
        }
        if (condicion == null || condicion.isEmpty()) {
            throw new IllegalArgumentException("condicion debe ser una cadena no vacía");
        }
        if (idDestino == null || idDestino.isEmpty()) {
            throw new IllegalArgumentException("idDestino debe ser una cadena no vacía");
        }
        camino = camino == null ? List.of() : List.copyOf(camino);
    }
}
