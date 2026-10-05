package org.signbb.backend.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.navegacion.modelo.Sintoma;
import org.signbb.backend.reglas.modelo.ReglaClinica;

/**
 * Estado serializable de una sesión de triaje para Redis.
 *
 * <p>
 * En vez de serializar el {@code ResultadoPaso} completo (que embebe nodos y
 * reglas enteras), se guardan las respuestas acumuladas (varias por nodo en
 * selección múltiple), las reglas ya disparadas y referencias por id: al
 * cargar, el paso se reconstruye resolviendo esas referencias contra el
 * contenido clínico vigente (ver {@code RepositorioSesionRedis}).
 *
 * <p>
 * {@code historial} es la pila de pasos del recorrido, que permite
 * retroceder sin reevaluar reglas. Una sesión guardada antes de que existiera
 * no lo trae ({@code null}); entonces el motor retrocede reproduciendo el
 * recorrido.
 */
public record SesionJson(
        String id,
        Map<String, List<String>> respuestas,
        List<String> reglasDisparadas,
        int puntaje,
        PasoJson ultimoPaso,
        List<PasoRecorridoJson> historial) {

    /** Un paso de la pila del recorrido (ver {@code PasoRecorrido}). */
    public record PasoRecorridoJson(
            String idPregunta,
            List<String> respuestas,
            List<String> respuestasPrevias,
            List<String> reglasMarcadas) {
    }

    /** Último paso del recorrido, con nodos y reglas referenciados por id. */
    public record PasoJson(
            String estado,
            String preguntaId,
            String nivel,
            String destinoId,
            List<String> razonesIds,
            List<CaminoJson> caminoCausal) {
    }

    /** Un paso del camino causal: el nodo y la condición que lo activó. */
    public record CaminoJson(String nodoId, String condicion) {
    }

    /** Serializa la sesión del dominio a su forma almacenable. */
    public static SesionJson desde(SesionTriaje sesion) {
        Map<String, List<String>> respuestas = new LinkedHashMap<>();
        for (Sintoma s : sesion.getEstado().sintomas()) {
            respuestas.computeIfAbsent(s.idNodo(), k -> new ArrayList<>()).add(s.respuesta());
        }

        ResultadoPaso paso = sesion.getUltimoPaso();
        PasoJson pasoJson = new PasoJson(
                paso.estado().name(),
                paso.preguntaActual() == null ? null : paso.preguntaActual().getId(),
                paso.nivel() == null ? null : paso.nivel().name(),
                paso.destino() == null ? null : paso.destino().getId(),
                paso.razones().stream().map(ReglaClinica::getIdRegla).toList(),
                paso.caminoCausal().stream()
                        .map(p -> new CaminoJson(p.nodo().getId(), p.condicion()))
                        .toList());
        return new SesionJson(sesion.getId(), respuestas,
                List.copyOf(sesion.getEstado().reglasDisparadas()),
                sesion.getEstado().getPuntaje(), pasoJson,
                sesion.getEstado().pasos().stream()
                        .map(p -> new PasoRecorridoJson(p.idPregunta(), p.respuestas(),
                                p.respuestasPrevias(), p.reglasMarcadas()))
                        .toList());
    }
}
