package org.signbb.backend.crobservaciones.modelo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * TAD SeleccionCuidador: lo que el cuidador marcó para la pregunta actual del
 * recorrido, antes de entregarse a
 * {@link org.signbb.backend.navegacion.servicios.ServicioNavegacion}.
 *
 * <p>
 * No debe confundirse con {@link org.signbb.backend.model.Respuesta}: esa es
 * la arista fija del grafo clínico (origen, destino, condición), definida una
 * sola vez al construir el grafo. Esta clase es el evento en tiempo de
 * ejecución de que un cuidador concreto, en un recorrido concreto, marcó una
 * o más opciones para un nodo concreto — no tiene destino ni forma parte del
 * grafo.
 *
 * <p>
 * Para SI_NO y SELECCION_UNICA, {@code opcionesSeleccionadas} trae
 * exactamente un elemento; para SELECCION_MULTIPLE, uno o más. En ambos
 * casos, cada elemento debe coincidir textualmente con la {@code condicion}
 * de alguna arista saliente del nodo actual (eso lo valida
 * {@code ServicioCapturaImpl} antes de construir esta clase).
 */
public final class SeleccionCuidador {

    private final String recorridoId;
    private final String idNodo;
    private final TipoInteraccion tipo;
    private final List<String> opcionesSeleccionadas;
    private final Instant timestamp;

    public SeleccionCuidador(String recorridoId, String idNodo, TipoInteraccion tipo,
            List<String> opcionesSeleccionadas) {
        if (recorridoId == null || recorridoId.isEmpty()) {
            throw new IllegalArgumentException("recorridoId debe ser una cadena no vacía");
        }
        if (idNodo == null || idNodo.isEmpty()) {
            throw new IllegalArgumentException("idNodo debe ser una cadena no vacía");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo debe ser TipoInteraccion");
        }
        if (opcionesSeleccionadas == null || opcionesSeleccionadas.isEmpty()) {
            throw new IllegalArgumentException("debe venir al menos una opción seleccionada");
        }
        if (tipo != TipoInteraccion.SELECCION_MULTIPLE && opcionesSeleccionadas.size() != 1) {
            throw new IllegalArgumentException(
                    "SI_NO y SELECCION_UNICA solo admiten exactamente una opción seleccionada");
        }
        this.recorridoId = recorridoId;
        this.idNodo = idNodo;
        this.tipo = tipo;
        this.opcionesSeleccionadas = List.copyOf(opcionesSeleccionadas);
        this.timestamp = Instant.now();
    }

    public String getRecorridoId() {
        return recorridoId;
    }

    public String getIdNodo() {
        return idNodo;
    }

    public TipoInteraccion getTipo() {
        return tipo;
    }

    public List<String> getOpcionesSeleccionadas() {
        return opcionesSeleccionadas;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SeleccionCuidador)) {
            return false;
        }
        SeleccionCuidador otra = (SeleccionCuidador) o;
        return recorridoId.equals(otra.recorridoId) && idNodo.equals(otra.idNodo)
                && timestamp.equals(otra.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recorridoId, idNodo, timestamp);
    }

    @Override
    public String toString() {
        return "SeleccionCuidador(recorrido='" + recorridoId + "', nodo='" + idNodo + "', tipo=" + tipo
                + ", opciones=" + opcionesSeleccionadas + ")";
    }
}