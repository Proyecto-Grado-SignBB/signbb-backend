package org.signbb.backend.navegacion.modelo;

import java.util.HashMap;
import java.util.Map;

import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Conserva en memoria el grafo clínico y la base de reglas usados por el
 * módulo de navegación, por versión del contenido.
 *
 * <p>
 * Cada publicación del módulo de autoría crea una versión del grafo
 * ({@link ResolvedorVersiones}). Un recorrido nuevo usa la versión vigente
 * (la más reciente) y uno en curso sigue con la versión con la que empezó,
 * pidiéndola con {@link #grafoDe(Long)} y {@link #baseDe(Long)}.
 * {@link #getGrafo()} y {@link #getBase()} son los de la versión vigente.
 *
 * <p>
 * En esta primera entrega solo el grafo está versionado: la base de reglas es
 * siempre la vigente, reconstruida sobre el grafo de la versión pedida. Las
 * versiones se cargan perezosamente (Redis como copia caliente, base de datos
 * como fuente de verdad) y la pereza garantiza que la siembra inicial
 * ({@code DatabaseInit}) haya corrido antes de la primera resolución.
 */
@Component
public class ContextoClinico {

    private static final Logger log = LoggerFactory.getLogger(ContextoClinico.class);

    /** Grafo y reglas ya reconstruidos de una versión. */
    private record Contenido(GrafoClinico grafo, BaseReglas base) {
    }

    private final ResolvedorContenidoClinico resolvedor;
    private final ResolvedorVersiones versiones;

    private Long idActual;
    private final Map<Long, Contenido> cargadas = new HashMap<>();

    /** Inyecta los resolvedores del contenido vigente y de las versiones. */
    public ContextoClinico(ResolvedorContenidoClinico resolvedor, ResolvedorVersiones versiones) {
        this.resolvedor = resolvedor;
        this.versiones = versiones;
    }

    /** Id de la versión vigente: la que usa un recorrido nuevo. */
    public synchronized Long idVersionActual() {
        if (idActual == null) {
            idActual = versiones.idActual();
        }
        return idActual;
    }

    /** Grafo clínico de la versión vigente. */
    public synchronized GrafoClinico getGrafo() {
        return contenido(idVersionActual()).grafo();
    }

    /** Base de reglas, armada sobre el grafo de la versión vigente. */
    public synchronized BaseReglas getBase() {
        return contenido(idVersionActual()).base();
    }

    /**
     * Grafo de una versión. Con {@code null} (recorridos guardados antes de
     * que existieran las versiones) es el de la versión vigente.
     */
    public synchronized GrafoClinico grafoDe(Long versionId) {
        return contenido(versionId == null ? idVersionActual() : versionId).grafo();
    }

    /** Base de reglas armada sobre el grafo de esa versión; {@code null} es la vigente. */
    public synchronized BaseReglas baseDe(Long versionId) {
        return contenido(versionId == null ? idVersionActual() : versionId).base();
    }

    /**
     * Descarta lo que hay en memoria para que el próximo uso lo vuelva a
     * resolver (y vea la versión vigente nueva). Lo llama el módulo de
     * autoría después de publicar un subgrafo, junto con
     * {@link ResolvedorContenidoClinico#invalidar()}.
     */
    public synchronized void recargar() {
        idActual = null;
        cargadas.clear();
    }

    private Contenido contenido(Long id) {
        Contenido c = cargadas.get(id);
        if (c == null) {
            c = cargar(id);
            cargadas.put(id, c);
        }
        return c;
    }

    /** Reconstruye y valida el grafo de la versión y la base de reglas vigente sobre él. */
    private Contenido cargar(Long id) {
        GrafoClinico g = GrafoClinicoMapper.desde(versiones.documentoGrafo(id));
        BaseReglas b = BaseReglasMapper.desde(resolvedor.documentoReglas(), g);

        if (!g.rutaValida()) {
            log.warn("El grafo clínico (versión {}) no cumple rutaValida(): puede haber nodos huérfanos "
                    + "o preguntas sin salida (p. ej. una pregunta sin arista de entrada).", id);
        }
        if (!g.validarCiclo()) {
            log.warn("El grafo clínico (versión {}) contiene ciclos: la navegación podría no terminar.", id);
        }
        log.info("Contexto clínico resuelto (versión {}): {} nodos, {} reglas.", id, g.size(), b.size());
        return new Contenido(g, b);
    }
}
