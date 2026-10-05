package org.signbb.backend.navegacion.datos.serializacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.springframework.core.io.ClassPathResource;

/**
 * Contenido clínico de ejemplo del classpath ({@code static/}): el grafo raíz,
 * el subgrafo de ictericia y su base de reglas.
 *
 * <p>
 * Es la semilla de {@code DatabaseInit} y el contenido de la demo y de los
 * tests; el runtime no lo lee (resuelve desde la base de datos). Reúne en un
 * solo lugar las rutas y la composición para que todos usen el mismo grafo.
 */
public final class ContenidoEjemplo {

    /** Grafo raíz del sistema. */
    public static final String RUTA_RAIZ = "static/ejemplo_grafo_raiz.json";

    /** Subgrafos publicados de ejemplo. */
    public static final List<String> RUTAS_SUBGRAFOS = List.of(
            "static/ejemplo_subgrafo_piel_ictericia.json");

    /** Bases de reglas de ejemplo, una por subgrafo. */
    public static final List<String> RUTAS_REGLAS = List.of(
            "static/ejemplo_reglas_piel_ictericia.json");

    private ContenidoEjemplo() {
    }

    /** Documento del grafo raíz. */
    public static GrafoClinicoJson raiz() {
        return GrafoClinicoParser.parseJsonToDocumento(leerRecurso(RUTA_RAIZ));
    }

    /** Documentos de los subgrafos de ejemplo. */
    public static List<GrafoClinicoJson> subgrafos() {
        return RUTAS_SUBGRAFOS.stream()
                .map(ruta -> GrafoClinicoParser.parseJsonToDocumento(leerRecurso(ruta)))
                .toList();
    }

    /** Documentos de las bases de reglas de ejemplo. */
    public static List<BaseReglasJson> bases() {
        return RUTAS_REGLAS.stream()
                .map(ruta -> BaseReglasParser.parseJsonToDocumento(leerRecurso(ruta)))
                .toList();
    }

    /** Grafo y reglas de ejemplo ya compuestos (planos, ids prefijados). */
    public static CompositorGrafoClinico.Contenido compuesto() {
        return CompositorGrafoClinico.componer(raiz(), subgrafos(), bases());
    }

    /** Lee un recurso del classpath como texto UTF-8. */
    public static String leerRecurso(String ruta) {
        try {
            return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
    }
}
