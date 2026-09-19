package org.signbb.backend.navegacion.modelo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Carga y conserva en memoria el grafo clínico y la base de reglas usados por
 * el módulo de navegación.
 *
 * <p>
 * Al arrancar la aplicación lee {@code static/ejemplo_grafo.json} y
 * {@code static/ejemplo_reglas.json} con {@link GrafoClinicoParser} y
 * {@link BaseReglasParser}, y valida el grafo
 * ({@code rutaValida}, {@code validarCiclo}), registrando una advertencia si
 * algo no cuadra (por ejemplo, un nodo huérfano inalcanzable). El grafo y las
 * reglas quedan disponibles para {@link MotorNavegacion} vía {@link #getGrafo}
 * y {@link #getBase}.
 */
@Component
public class ContextoClinico {

    private static final Logger log = LoggerFactory.getLogger(ContextoClinico.class);

    private final GrafoClinico grafo;
    private final BaseReglas base;

    /** Carga grafo y reglas de los recursos del classpath y valida el grafo. */
    public ContextoClinico() {
        this.grafo = GrafoClinicoParser.parseJsonToGrafoClinico(
                leerRecurso("static/ejemplo_grafo.json"));
        this.base = BaseReglasParser.parseJsonToBaseReglas(
                leerRecurso("static/ejemplo_reglas.json"), grafo);

        if (!grafo.rutaValida()) {
            log.warn("El grafo clínico no cumple rutaValida(): puede haber nodos huérfanos "
                    + "o preguntas sin salida (p. ej. 'pregunta_poposangre' sin arista de entrada).");
        }
        if (!grafo.validarCiclo()) {
            log.warn("El grafo clínico contiene ciclos: la navegación podría no terminar.");
        }
        log.info("Contexto clínico cargado: {} nodos, {} reglas.", grafo.size(), base.size());
    }

    /** Grafo clínico cargado en memoria. */
    public GrafoClinico getGrafo() {
        return grafo;
    }

    /** Base de reglas cargada en memoria. */
    public BaseReglas getBase() {
        return base;
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
