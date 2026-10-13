package org.signbb.backend.navegacion.modelo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

/** Apoyo de las pruebas de versiones: una segunda versión del grafo que se distingue de la primera. */
public final class VersionesDePrueba {

    /** Opción que la versión 2 agrega a la pregunta inicial. */
    public static final String OPCION_NUEVA = "opción nueva";

    private VersionesDePrueba() {
    }

    /**
     * Copia del grafo con una opción más en {@code pregunta_inicial}, que lleva
     * a la pregunta de signos de alarma de respiración.
     */
    public static GrafoClinicoJson conOpcionExtra(GrafoClinicoJson grafo) {
        GrafoClinicoJson.Grafo g = grafo.graph();
        List<GrafoClinicoJson.Arista> aristas = new ArrayList<>(g.edges());
        aristas.add(new GrafoClinicoJson.Arista("pregunta_inicial", OPCION_NUEVA,
                "grafo_respiracion/q_resp_urgencia", true));
        Map<String, GrafoClinicoJson.NodoJson> nodos = new LinkedHashMap<>(g.nodes());
        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo(g.id(), g.label(), g.directed(), g.category(),
                g.preguntaInicial(), g.nodoInicial(), g.metadata(), nodos, aristas));
    }
}
