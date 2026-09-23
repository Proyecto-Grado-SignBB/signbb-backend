package org.signbb.backend.navegacion.datos.serializacion;

import org.signbb.backend.navegacion.modelo.GrafoClinico;

import tools.jackson.databind.ObjectMapper;

/**
 * Convierte el JSON del grafo clínico ({@code graph}) en el TAD
 * {@link GrafoClinico}.
 *
 * <p>
 * El enlace JSON → {@link GrafoClinicoJson} lo hace Jackson sobre los records
 * del documento; la construcción del TAD (y sus validaciones) queda en
 * {@link GrafoClinicoMapper}.
 */
public class GrafoClinicoParser {

    /** Carga el grafo clínico desde el JSON con la clave {@code graph}. */
    public static GrafoClinico parseJsonToGrafoClinico(String jsonFile) {
        return GrafoClinicoMapper.desde(parseJsonToDocumento(jsonFile));
    }

    /** Carga el documento JSON de intercambio sin construir el TAD. */
    public static GrafoClinicoJson parseJsonToDocumento(String jsonFile) {
        return new ObjectMapper().readValue(jsonFile, GrafoClinicoJson.class);
    }
}
