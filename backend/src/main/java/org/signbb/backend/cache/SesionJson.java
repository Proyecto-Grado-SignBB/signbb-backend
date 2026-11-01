package org.signbb.backend.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.signbb.backend.navegacion.modelo.FotoNavegacion;
import org.signbb.backend.navegacion.modelo.OpcionPendiente;
import org.signbb.backend.navegacion.modelo.PasoCausal;
import org.signbb.backend.navegacion.modelo.PasoRuta;
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
 * retroceder sin reevaluar reglas. {@code navegacion} es la cola de opciones
 * pendientes de la selección múltiple, la ruta que se está siguiendo y las
 * recomendaciones ya alcanzadas. El formato actual siempre trae ambos campos.
 * En una sesión guardada antes de que existieran pueden venir {@code null}:
 * sin historial la pila queda vacía y no se puede retroceder, y sin navegación
 * arranca con la cola vacía y reconstruye la ruta cuando la necesita.
 *
 * <p>
 * {@code versionId} es la versión del contenido clínico con la que empezó el
 * recorrido; las sesiones anteriores a las versiones no la traen ({@code null})
 * y usan la vigente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SesionJson(
        String id,
        Map<String, List<String>> respuestas,
        List<String> reglasDisparadas,
        PasoJson ultimoPaso,
        List<PasoRecorridoJson> historial,
        NavegacionJson navegacion,
        Long versionId) {

    /**
     * Un paso de la pila del recorrido (ver {@code PasoRecorrido}).
     * {@code navegacionPrevia} es la foto de la navegación antes del paso
     * ({@code null} en pasos guardados antes de existir).
     */
    public record PasoRecorridoJson(
            String idPregunta,
            List<String> respuestas,
            List<String> respuestasPrevias,
            List<String> reglasMarcadas,
            NavegacionJson navegacionPrevia) {
    }

    /**
     * Último paso del recorrido, con nodos y reglas referenciados por id.
     * {@code recomendaciones} solo viene en un cierre sin alerta (puede ser
     * {@code null} en sesiones guardadas antes de existir).
     */
    public record PasoJson(
            String estado,
            String preguntaId,
            String nivel,
            String destinoId,
            List<String> razonesIds,
            List<CaminoJson> caminoCausal,
            List<RecomendacionJson> recomendaciones) {
    }

    /** Un paso del camino causal o de la ruta: el nodo y la condición que lo activó. */
    public record CaminoJson(String nodoId, String condicion) {
    }

    /** Una recomendación alcanzada: el nodo y el camino que llevó a él. */
    public record RecomendacionJson(String nodoId, List<CaminoJson> camino) {
    }

    /** Una opción marcada y todavía sin seguir (ver {@code OpcionPendiente}). */
    public record OpcionJson(String idOrigen, String condicion, String idDestino, int orden,
            List<CaminoJson> camino) {
    }

    /** Cola de pendientes, ruta y recomendaciones alcanzadas (ver {@code FotoNavegacion}). */
    public record NavegacionJson(
            List<OpcionJson> cola,
            List<CaminoJson> ruta,
            List<RecomendacionJson> recomendaciones,
            int siguienteOrden) {

        /** Forma serializable de una foto de navegación. */
        static NavegacionJson desde(FotoNavegacion foto) {
            List<OpcionJson> cola = new ArrayList<>();
            for (OpcionPendiente o : foto.cola()) {
                cola.add(new OpcionJson(o.idOrigen(), o.condicion(), o.idDestino(), o.orden(),
                        rutaJson(o.camino())));
            }
            List<RecomendacionJson> recomendaciones = new ArrayList<>();
            for (Map.Entry<String, List<PasoRuta>> e : foto.recomendaciones().entrySet()) {
                recomendaciones.add(new RecomendacionJson(e.getKey(), rutaJson(e.getValue())));
            }
            return new NavegacionJson(cola, rutaJson(foto.ruta()), recomendaciones,
                    foto.siguienteOrden());
        }

        /** Foto de navegación del dominio (las listas ausentes se toman como vacías). */
        FotoNavegacion aFoto() {
            List<OpcionPendiente> opciones = new ArrayList<>();
            if (cola != null) {
                for (OpcionJson o : cola) {
                    opciones.add(new OpcionPendiente(o.idOrigen(), o.condicion(), o.idDestino(),
                            o.orden(), pasosRuta(o.camino())));
                }
            }
            Map<String, List<PasoRuta>> alcanzadas = new LinkedHashMap<>();
            if (recomendaciones != null) {
                for (RecomendacionJson r : recomendaciones) {
                    alcanzadas.put(r.nodoId(), pasosRuta(r.camino()));
                }
            }
            return new FotoNavegacion(opciones, pasosRuta(ruta), alcanzadas, siguienteOrden);
        }
    }

    private static List<CaminoJson> rutaJson(List<PasoRuta> ruta) {
        return ruta.stream().map(p -> new CaminoJson(p.idNodo(), p.condicion())).toList();
    }

    private static List<PasoRuta> pasosRuta(List<CaminoJson> camino) {
        List<PasoRuta> ruta = new ArrayList<>();
        if (camino != null) {
            for (CaminoJson c : camino) {
                ruta.add(new PasoRuta(c.nodoId(), c.condicion()));
            }
        }
        return ruta;
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
                caminoJson(paso.caminoCausal()),
                paso.recomendaciones().stream()
                        .map(r -> new RecomendacionJson(r.nodo().getId(), caminoJson(r.camino())))
                        .toList());
        return new SesionJson(sesion.getId(), respuestas,
                List.copyOf(sesion.getEstado().reglasDisparadas()), pasoJson,
                sesion.getEstado().pasos().stream()
                        .map(p -> new PasoRecorridoJson(p.idPregunta(), p.respuestas(),
                                p.respuestasPrevias(), p.reglasMarcadas(),
                                p.navegacionPrevia() == null ? null
                                        : NavegacionJson.desde(p.navegacionPrevia())))
                        .toList(),
                NavegacionJson.desde(sesion.getEstado().fotografiarNavegacion()),
                sesion.getVersionId());
    }

    private static List<CaminoJson> caminoJson(List<PasoCausal> camino) {
        return camino.stream().map(p -> new CaminoJson(p.nodo().getId(), p.condicion())).toList();
    }
}
