package org.signbb.backend.navegacion.modelo;

import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Conserva en memoria el grafo clínico y la base de reglas usados por el
 * módulo de navegación.
 *
 * <p>
 * MODIFICADO (cache): el contenido ya no se lee del classpath en el
 * constructor. Se resuelve perezosamente en el primer uso a través de
 * {@link ResolvedorContenidoClinico} (Redis como copia caliente, base de
 * datos como fuente de verdad) y el TAD resultante queda en memoria para
 * atender las peticiones: Redis nunca está en el camino de cada request, y
 * la pereza garantiza que la siembra inicial ({@code DatabaseInit}) haya
 * corrido antes de la primera resolución.
 */
@Component
public class ContextoClinico {

    private static final Logger log = LoggerFactory.getLogger(ContextoClinico.class);

    private final ResolvedorContenidoClinico resolvedor;

    private GrafoClinico grafo;
    private BaseReglas base;

    /** Inyecta el resolvedor de contenido clínico (cache-aside). */
    public ContextoClinico(ResolvedorContenidoClinico resolvedor) {
        this.resolvedor = resolvedor;
    }

    /** Grafo clínico en memoria; lo resuelve del cache/BD en el primer uso. */
    public synchronized GrafoClinico getGrafo() {
        asegurarContenido();
        return grafo;
    }

    /** Base de reglas en memoria; la resuelve del cache/BD en el primer uso. */
    public synchronized BaseReglas getBase() {
        asegurarContenido();
        return base;
    }

    /** Resuelve y valida el contenido una sola vez (perezoso). */
    private void asegurarContenido() {
        if (grafo != null) {
            return;
        }
        GrafoClinico g = GrafoClinicoMapper.desde(resolvedor.documentoGrafo());
        BaseReglas b = BaseReglasMapper.desde(resolvedor.documentoReglas(), g);

        if (!g.rutaValida()) {
            log.warn("El grafo clínico no cumple rutaValida(): puede haber nodos huérfanos "
                    + "o preguntas sin salida (p. ej. 'pregunta_poposangre' sin arista de entrada).");
        }
        if (!g.validarCiclo()) {
            log.warn("El grafo clínico contiene ciclos: la navegación podría no terminar.");
        }
        log.info("Contexto clínico resuelto: {} nodos, {} reglas.", g.size(), b.size());
        this.grafo = g;
        this.base = b;
    }
}
