package org.signbb.backend.navegacion.datos.serializacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.springframework.core.io.ClassPathResource;

/**
 * Contenido clínico de ejemplo: el grafo raíz del sistema y, para tests y
 * demo, los tres subgrafos de respiración, gastrointestinal y actividad/color,
 * cada uno con su base de reglas.
 *
 * <p>
 * Solo el grafo raíz es semilla de {@code DatabaseInit}. Los subgrafos y sus
 * reglas viven en el classpath de pruebas ({@code src/test/resources}) y no se
 * cargan en el runtime: se usarán en la fase de datos (seed de pruebas y
 * demo). Reúne en un solo lugar las rutas y la composición para que tests y
 * demo usen el mismo contenido.
 */
public final class ContenidoEjemplo {

    /** Grafo raíz del sistema. */
    public static final String RUTA_RAIZ = "static/grafo_sistema/grafo_raiz.json";

    /** Subgrafos de ejemplo (classpath de pruebas), uno por categoría. */
    public static final List<String> RUTAS_SUBGRAFOS = List.of(
            "static/grafo_sistema/grafo_respiracion.json",
            "static/grafo_sistema/grafo_gastro.json",
            "static/grafo_sistema/grafo_activo_color.json");

    /** Bases de reglas de los subgrafos de ejemplo (classpath de pruebas). */
    public static final List<String> RUTAS_REGLAS = List.of(
            "static/grafo_sistema/reglas_respiracion.json",
            "static/grafo_sistema/reglas_gastro.json",
            "static/grafo_sistema/reglas_activo_color.json");

    private ContenidoEjemplo() {
    }

    /** Documento del grafo raíz. */
    public static GrafoClinicoJson raiz() {
        return GrafoClinicoParser.parseJsonToDocumento(leerRecurso(RUTA_RAIZ));
    }

    /** Documentos de los subgrafos publicados. */
    public static List<GrafoClinicoJson> subgrafos() {
        return RUTAS_SUBGRAFOS.stream()
                .map(ruta -> GrafoClinicoParser.parseJsonToDocumento(leerRecurso(ruta)))
                .toList();
    }

    /** Documentos de las bases de reglas, una por subgrafo. */
    public static List<BaseReglasJson> bases() {
        return RUTAS_REGLAS.stream()
                .map(ruta -> BaseReglasParser.parseJsonToDocumento(leerRecurso(ruta)))
                .toList();
    }

    /** Grafo y reglas ya compuestos (planos, ids prefijados). */
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
