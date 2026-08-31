package org.signbb.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TAD Contexto (Sección 4.2 del documento de diseño).
 *
 * <p>Guarda el historial de un paciente a lo largo de múltiples sesiones de
 * triaje. Agrega {@link EstadoPaciente} como sub-estructura.
 *
 * <p>Migrado desde {@code src/contexto.py}.
 */
public final class Contexto {

    private final String idPaciente;
    private final List<EstadoPaciente> sesiones = new ArrayList<>();
    private int puntajeGlobal = 0;

    public Contexto(String idPaciente) {
        if (idPaciente == null || idPaciente.isEmpty()) {
            throw new IllegalArgumentException("idPaciente debe ser una cadena no vacía");
        }
        this.idPaciente = idPaciente;
    }

    public String getIdPaciente() {
        return idPaciente;
    }

    public EstadoPaciente nuevaSesion(String idSesion) {
        for (EstadoPaciente s : sesiones) {
            if (s.getIdSesion().equals(idSesion)) {
                throw new IllegalArgumentException(
                        "ya existe una sesión con id '" + idSesion + "' en este contexto");
            }
        }
        EstadoPaciente sesion = new EstadoPaciente(idSesion);
        sesiones.add(sesion);
        return sesion;
    }

    /** Última sesión creada, o vacío si aún no hay sesiones. */
    public Optional<EstadoPaciente> sesionActual() {
        if (sesiones.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sesiones.get(sesiones.size() - 1));
    }

    /** Copia del historial de sesiones. */
    public List<EstadoPaciente> historial() {
        return new ArrayList<>(sesiones);
    }

    public void sumarPuntajeGlobal(int delta) {
        puntajeGlobal += delta;
    }

    public int getPuntajeGlobal() {
        return puntajeGlobal;
    }

    /** Número de sesiones en el contexto. */
    public int size() {
        return sesiones.size();
    }

    @Override
    public String toString() {
        return "Contexto(paciente='" + idPaciente + "', sesiones=" + sesiones.size()
                + ", puntajeGlobal=" + puntajeGlobal + ")";
    }
}
