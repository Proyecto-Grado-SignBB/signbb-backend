package org.signbb.backend.navegacion.datos.serializacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.springframework.core.io.ClassPathResource;

/**
 * Contenido clínico real del sistema: el grafo raíz que siembra
 * {@code DatabaseInit} y los subgrafos con sus reglas del seed de demo.
 *
 * <p>
 * La raíz y los subgrafos viven en {@code src/main/resources}: la misma fuente
 * que usa el sistema en runtime. Los tests cargan este contenido para no
 * mantener una copia distinta de las categorías, preguntas y opciones.
 */
public final class ContenidoEjemplo {

    /** Grafo raíz del sistema (el mismo que siembra {@code DatabaseInit}). */
    public static final String RUTA_RAIZ = "static/grafo_sistema/grafo_raiz.json";

    /** Subgrafos publicados por el seed de demo, uno por categoría. */
    public static final List<String> RUTAS_SUBGRAFOS = List.of(
            "seed/demo/grafo_respiracion.json",
            "seed/demo/grafo_gastro.json",
            "seed/demo/grafo_activo_color.json",
            "seed/demo/grafo_cabeza.json");

    /** Bases de reglas de los subgrafos del seed de demo. */
    public static final List<String> RUTAS_REGLAS = List.of(
            "seed/demo/reglas_respiracion.json",
            "seed/demo/reglas_gastro.json",
            "seed/demo/reglas_activo_color.json",
            "seed/demo/reglas_cabeza.json");

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
