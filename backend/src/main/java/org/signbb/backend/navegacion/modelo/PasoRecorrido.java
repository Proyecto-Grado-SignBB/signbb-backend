package org.signbb.backend.navegacion.modelo;

import java.util.List;

/**
 * Una respuesta del cuidador tal como quedó anotada en la pila de pasos de
 * {@link EstadoPaciente}: qué pregunta respondió, con qué opciones y qué
 * reglas marcó como disparadas esa respuesta. Es lo que hace falta para
 * deshacerla sin reevaluar las reglas.
 *
 * @param idPregunta        pregunta respondida.
 * @param respuestas        opciones marcadas en este paso.
 * @param respuestasPrevias lo que la pregunta tenía registrado antes de este
 *                          paso (vacío si no tenía nada). Solo no es vacío si
 *                          una regla "compuerta" llevó a una pregunta ya
 *                          respondida; al deshacer se restaura.
 * @param reglasMarcadas    reglas que este paso marcó como disparadas.
 * @param navegacionPrevia  foto del estado de navegación (cola de opciones
 *                          pendientes, ruta y recomendaciones) tomada antes de
 *                          este paso; al deshacer se restaura. Es {@code null}
 *                          en pasos guardados antes de existir la cola, que
 *                          entonces no tocan la navegación al deshacerse.
 */
public record PasoRecorrido(String idPregunta, List<String> respuestas,
        List<String> respuestasPrevias, List<String> reglasMarcadas,
        FotoNavegacion navegacionPrevia) {

    /** Valida el id y guarda copias inmutables de las listas. */
    public PasoRecorrido {
        if (idPregunta == null || idPregunta.isEmpty()) {
            throw new IllegalArgumentException("idPregunta debe ser una cadena no vacía");
        }
        respuestas = respuestas == null ? List.of() : List.copyOf(respuestas);
        respuestasPrevias = respuestasPrevias == null ? List.of() : List.copyOf(respuestasPrevias);
        reglasMarcadas = reglasMarcadas == null ? List.of() : List.copyOf(reglasMarcadas);
    }

    /**
     * Paso sin foto de navegación (el formato anterior a la cola de opciones
     * pendientes): al deshacerlo no se modifica la cola, la ruta ni las
     * recomendaciones.
     */
    public PasoRecorrido(String idPregunta, List<String> respuestas,
            List<String> respuestasPrevias, List<String> reglasMarcadas) {
        this(idPregunta, respuestas, respuestasPrevias, reglasMarcadas, null);
    }
}
