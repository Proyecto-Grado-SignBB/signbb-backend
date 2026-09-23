package org.signbb.backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Inicialización de la base de datos: siembra el grafo clínico y la base de
 * reglas de ejemplo ({@code static/ejemplo_grafo.json} y
 * {@code static/ejemplo_reglas.json}) cuando sus tablas están vacías.
 *
 * <p>
 * Antes de guardar cada documento se reconstruye el TAD con los parsers, de
 * modo que solo se persisten datos válidos (referencias colgantes, niveles
 * inválidos o aristas rotas fallan aquí). El documento se guarda tal cual
 * viene del JSON para conservar los metadatos de presentación (autor,
 * categorías) que el TAD no almacena, completando el {@code nodoInicial}
 * explícito si el archivo no lo trae. La siembra es idempotente: si las
 * tablas ya tienen filas, no hace nada.
 */
@Component
public class DatabaseInit implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInit.class);

    private final RepositorioGrafoClinico repositorioGrafos;
    private final RepositorioBaseReglas repositorioBases;

    /** Inyecta los repositorios de grafos clínicos y bases de reglas. */
    public DatabaseInit(RepositorioGrafoClinico repositorioGrafos,
            RepositorioBaseReglas repositorioBases) {
        this.repositorioGrafos = repositorioGrafos;
        this.repositorioBases = repositorioBases;
    }

    /** Siembra grafo y reglas de ejemplo si sus tablas están vacías. */
    @Override
    public void run(String... args) {
        long grafos = repositorioGrafos.count();
        long bases = repositorioBases.count();
        if (grafos > 0 && bases > 0) {
            log.info("Base de datos ya inicializada: {} grafo(s), {} base(s) de reglas.",
                    grafos, bases);
            return;
        }
        if (grafos == 0) {
            sembrarGrafo();
        }
        if (bases == 0) {
            sembrarBaseReglas();
        }
    }

    /** Guarda ejemplo_grafo.json tras validar que el TAD se reconstruye. */
    private void sembrarGrafo() {
        String json = leerRecurso("static/ejemplo_grafo.json");
        GrafoClinico grafo = GrafoClinicoParser.parseJsonToGrafoClinico(json);
        if (!grafo.rutaValida()) {
            log.warn("El grafo sembrado no cumple rutaValida(): puede haber nodos huérfanos "
                    + "o preguntas sin salida.");
        }
        if (!grafo.validarCiclo()) {
            log.warn("El grafo sembrado contiene ciclos: la navegación podría no terminar.");
        }

        GrafoClinicoEntity entidad = new GrafoClinicoEntity();
        entidad.setEstructura(conNodoInicialExplicito(
                GrafoClinicoParser.parseJsonToDocumento(json), grafo));
        repositorioGrafos.save(entidad);
        log.info("Grafo clínico de ejemplo sembrado: {} nodos.", grafo.size());
    }

    /**
     * Completa {@code nodoInicial} con el inicial del TAD cuando el documento
     * no lo trae, de modo que la fila persistida quede autocontenida (sin
     * depender de la convención "primer nodo insertado").
     */
    private static GrafoClinicoJson conNodoInicialExplicito(
            GrafoClinicoJson documento, GrafoClinico tad) {
        GrafoClinicoJson.Grafo g = documento.graph();
        if (g.nodoInicial() != null) {
            return documento;
        }
        String inicial = tad.obtenerNodoInicial().map(Nodo::getId).orElse(null);
        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo(
                g.label(), g.metadata(), inicial, g.nodes(), g.edges()));
    }

    /** Guarda ejemplo_reglas.json tras validarlo contra el grafo de ejemplo. */
    private void sembrarBaseReglas() {
        String jsonGrafo = leerRecurso("static/ejemplo_grafo.json");
        GrafoClinico grafo = GrafoClinicoParser.parseJsonToGrafoClinico(jsonGrafo);

        String json = leerRecurso("static/ejemplo_reglas.json");
        BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(json, grafo);

        BaseReglasEntity entidad = new BaseReglasEntity();
        entidad.setEstructura(BaseReglasParser.parseJsonToDocumento(json));
        repositorioBases.save(entidad);
        log.info("Base de reglas de ejemplo sembrada: {} reglas.", base.size());
    }

    /** Lee un recurso del classpath como texto UTF-8. */
    private static String leerRecurso(String ruta) {
        try {
            return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
    }
}
