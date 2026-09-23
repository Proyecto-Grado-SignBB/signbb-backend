package org.signbb.backend.reglas.datos.serializacion;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.reglas.modelo.BaseReglas;

import tools.jackson.databind.ObjectMapper;

/**
 * Convierte el JSON de la base de reglas ({@code rules}) en el TAD
 * {@link BaseReglas}.
 *
 * <p>
 * El enlace JSON → {@link BaseReglasJson} lo hace Jackson sobre los records
 * del documento; la construcción del TAD (resolución de {@code nodoDestino}
 * contra el grafo y validaciones) queda en {@link BaseReglasMapper}.
 */
public class BaseReglasParser {

    /**
     * Carga la base de reglas desde el JSON con la clave {@code rules},
     * resolviendo cada {@code nodoDestino} contra los nodos del grafo.
     */
    public static BaseReglas parseJsonToBaseReglas(String jsonFile, GrafoClinico grafo) {
        return BaseReglasMapper.desde(parseJsonToDocumento(jsonFile), grafo);
    }

    /** Carga el documento JSON de intercambio sin construir el TAD. */
    public static BaseReglasJson parseJsonToDocumento(String jsonFile) {
        return new ObjectMapper().readValue(jsonFile, BaseReglasJson.class);
    }
}
