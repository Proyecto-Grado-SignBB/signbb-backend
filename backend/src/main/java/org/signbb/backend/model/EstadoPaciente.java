package org.signbb.backend.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TAD EstadoPaciente (Sección 4.2 del documento de diseño).
 *
 * <p>Representa una sesión de triaje en curso: las respuestas del padre a las
 * preguntas del grafo y el puntaje acumulado. No contiene lógica de
 * inferencia — solo almacena y consulta.
 *
 * <p>Se usa {@link LinkedHashMap} para conservar el orden de inserción de las
 * respuestas (mismo comportamiento determinista que el dict de Python).
 *
 * <p>Migrado desde {@code src/estado_paciente.py}.
 */
public final class EstadoPaciente {

    private final String idSesion;
    private final Map<String, String> respuestas = new LinkedHashMap<>();
    private int puntaje = 0;

    public EstadoPaciente(String idSesion) {
        if (idSesion == null || idSesion.isEmpty()) {
            throw new IllegalArgumentException("idSesion debe ser una cadena no vacía");
        }
        this.idSesion = idSesion;
    }

    public String getIdSesion() {
        return idSesion;
    }

    public void registrarRespuesta(String idNodo, String respuesta) {
        if (idNodo == null || idNodo.isEmpty()) {
            throw new IllegalArgumentException("idNodo debe ser una cadena no vacía");
        }
        if (respuesta == null || respuesta.isEmpty()) {
            throw new IllegalArgumentException("respuesta debe ser una cadena no vacía");
        }
        respuestas.put(idNodo, respuesta);
    }

    /** Devuelve la respuesta registrada para {@code idNodo}, si existe. */
    public Optional<String> getRespuesta(String idNodo) {
        return Optional.ofNullable(respuestas.get(idNodo));
    }

    public void sumarPuntaje(int delta) {
        puntaje += delta;
    }

    public int getPuntaje() {
        return puntaje;
    }

    /** Copia de los síntomas registrados (no expone la estructura interna). */
    public List<Sintoma> sintomas() {
        List<Sintoma> lista = new ArrayList<>();
        for (Map.Entry<String, String> e : respuestas.entrySet()) {
            lista.add(new Sintoma(e.getKey(), e.getValue()));
        }
        return lista;
    }

    public void reiniciar() {
        respuestas.clear();
        puntaje = 0;
    }

    /** Número de respuestas registradas. */
    public int size() {
        return respuestas.size();
    }

    @Override
    public String toString() {
        return "EstadoPaciente(idSesion='" + idSesion + "', respuestas="
                + respuestas.size() + ", puntaje=" + puntaje + ")";
    }
}
