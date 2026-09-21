package org.signbb.backend.observaciones.controladores;

import java.util.List;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.Respuesta;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.reglas.modelo.ReglaClinica;

/**
 * Vista REST de un recorrido de triaje.
 *
 * <p>
 * Si {@code estado} es {@code EN_CURSO}, lleva la {@code pregunta} que debe
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
                        PreguntaDTO pregunta = new PreguntaDTO(
                                        actual.getId(),
                                        actual.getContenido(),
                                        opciones,
                                        actual.tieneArchivos(),
                                        actual.getSrc(),
                                        resolverTipoInteraccion(actual, opciones));
                        return new RecorridoDTO(recorridoId, paso.estado().name(), pregunta, null);
                }

                Nodo destino = paso.destino();
                List<String> razones = paso.razones().stream()
                                .map(ReglaClinica::getNombre)
                                .toList();
                List<PasoCausalDTO> camino = paso.caminoCausal().stream()
                                .map(p -> new PasoCausalDTO(p.nodo().getId(), p.condicion()))
                                .toList();
                ResultadoDTO resultado = new ResultadoDTO(
                                paso.nivel().name(), destino.getId(), destino.getContenido(), razones, camino);
                return new RecorridoDTO(recorridoId, paso.estado().name(), null, resultado);
        }

        /**
         * NUEVO: resuelve el {@code tipoInteraccion} de la pregunta. Usa el
         * override explícito de {@code metadata.tipoInteraccion} del nodo si
         * viene; si no, infiere {@code SI_NO} cuando las opciones son
         * exactamente {SI, NO}, y {@code SELECCION_UNICA} en cualquier otro
         * caso — así ningún grafo existente cambia de comportamiento.
         * {@code SELECCION_MULTIPLE} solo se obtiene marcándolo explícitamente
         * en la metadata del nodo en el grafo JSON.
         */
        private static String resolverTipoInteraccion(Nodo actual, List<String> opciones) {
                if (actual.getTipoInteraccion() != null) {
                        return actual.getTipoInteraccion();
                }
                boolean esSiNo = opciones.size() == 2
                                && opciones.contains("SI")
                                && opciones.contains("NO");
                return esSiNo ? "SI_NO" : "SELECCION_UNICA";
        }
}