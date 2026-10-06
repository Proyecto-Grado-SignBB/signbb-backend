package org.signbb.backend.navegacion.modelo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copia inmutable del estado de navegación de una sesión en un instante: la
 * cola de opciones pendientes, la ruta que se está siguiendo, las
 * recomendaciones ya alcanzadas y el contador de orden de llegada.
 *
 * <p>
 * Cada {@link PasoRecorrido} guarda la foto tomada <b>antes</b> de ejecutarse;
 * deshacer el paso es restaurarla, lo que es más seguro que calcular qué
 * entró y qué salió de la cola.
 *
 * @param cola            opciones pendientes, en el orden en que llegaron.
 * @param ruta            recorrido real hasta la pregunta pendiente.
 * @param recomendaciones recomendaciones alcanzadas (id del nodo → camino),
 *                        en el orden en que se alcanzaron.
 * @param siguienteOrden  número de orden que recibirá la próxima opción
 *                        encolada.
 */
public record FotoNavegacion(List<OpcionPendiente> cola, List<PasoRuta> ruta,
        Map<String, List<PasoRuta>> recomendaciones, int siguienteOrden) {

    /** Guarda copias inmutables (el orden de las recomendaciones se conserva). */
    public FotoNavegacion {
        cola = cola == null ? List.of() : List.copyOf(cola);
        ruta = ruta == null ? List.of() : List.copyOf(ruta);
        Map<String, List<PasoRuta>> copia = new LinkedHashMap<>();
        if (recomendaciones != null) {
            for (Map.Entry<String, List<PasoRuta>> e : recomendaciones.entrySet()) {
                copia.put(e.getKey(), List.copyOf(e.getValue()));
            }
        }
        recomendaciones = Collections.unmodifiableMap(copia);
    }
}
