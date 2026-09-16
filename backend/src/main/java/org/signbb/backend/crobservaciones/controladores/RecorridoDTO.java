package org.signbb.backend.crobservaciones.controladores;

import java.util.List;

import org.signbb.backend.model.GrafoClinico;
import org.signbb.backend.model.Nodo;
import org.signbb.backend.model.PasoCausal;
import org.signbb.backend.model.ReglaClinica;
import org.signbb.backend.model.Respuesta;
import org.signbb.backend.navegacion.motor.ResultadoPaso;

/**
 * Vista REST de un recorrido de triaje.
 *
 * <p>Si {@code estado} es {@code EN_CURSO}, lleva la {@code pregunta} que debe
 * responderse y {@code resultado} es {@code null}. Si es {@code FINALIZADO},
 * lleva el {@code resultado} y {@code pregunta} es {@code null}.
 */
public record RecorridoDTO(
        String recorridoId,
        String estado,
        PreguntaDTO pregunta,
        ResultadoDTO resultado) {

    /**
     * Construye el DTO a partir de un {@link ResultadoPaso}, resolviendo las
     * opciones de la pregunta actual contra el grafo.
     */
    public static RecorridoDTO desde(String recorridoId, GrafoClinico grafo, ResultadoPaso paso) {
        if (!paso.finalizado()) {
            Nodo actual = paso.preguntaActual();
            List<String> opciones = grafo.obtenerOpciones(actual).stream()
                    .map(Respuesta::getCondicion)
                    .toList();
            PreguntaDTO pregunta = new PreguntaDTO(actual.getId(), actual.getContenido(), opciones);
            return new RecorridoDTO(recorridoId, paso.estado().name(), pregunta, null);
        }

        Nodo destino = paso.destino();
        List<String> razones = paso.razones().stream()
                .map(ReglaClinica::getIdRegla)
                .toList();
        List<PasoCausalDTO> camino = paso.caminoCausal().stream()
                .map(p -> new PasoCausalDTO(p.nodo().getId(), p.condicion()))
                .toList();
        ResultadoDTO resultado = new ResultadoDTO(
                paso.nivel().name(), destino.getId(), destino.getContenido(), razones, camino);
        return new RecorridoDTO(recorridoId, paso.estado().name(), null, resultado);
    }
}
