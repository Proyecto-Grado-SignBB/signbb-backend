package org.signbb.backend.navegacion.modelo;

import java.util.List;

import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;

/**
 * Resultado de un paso del {@link MotorNavegacion}: la descomposición por
 * pasos del Algoritmo 3 (una iteración del bucle {@code while}).
 *
 * <p>
 * Cuando el recorrido sigue abierto ({@code EN_CURSO}) el resultado lleva la
 * {@code preguntaActual} que debe responderse a continuación. Cuando el
 * recorrido termina ({@code FINALIZADO}) lleva el {@code nivel} de alerta, el
 * nodo {@code destino} alcanzado, las {@code razones} (reglas de nivel máximo
 * que dispararon; vacío si el desenlace es NORMAL) y el {@code caminoCausal}
 * reconstruido hasta el destino.
 *
 * <p>
 * Cuando el recorrido termina sin alerta, {@code recomendaciones} trae todas
 * las recomendaciones alcanzadas, cada una con su propio camino; en un
 * resultado con alerta, o con el recorrido en curso, está vacía.
 */
public record ResultadoPaso(
        Estado estado,
        Nodo preguntaActual,
        NivelAlerta nivel,
        Nodo destino,
        List<ReglaClinica> razones,
        List<PasoCausal> caminoCausal,
        List<Recomendacion> recomendaciones) {

    /** Estado de avance de un recorrido. */
    public enum Estado {
        /** Falta responder al menos una pregunta más. */
        EN_CURSO,
        /** El recorrido terminó (por regla o por terminal del grafo). */
        FINALIZADO
    }

    /** Guarda una copia inmutable de las recomendaciones (nula equivale a vacía). */
    public ResultadoPaso {
        recomendaciones = recomendaciones == null ? List.of() : List.copyOf(recomendaciones);
    }

    /** Resultado de un solo destino: sin lista de recomendaciones. */
    public ResultadoPaso(Estado estado, Nodo preguntaActual, NivelAlerta nivel, Nodo destino,
            List<ReglaClinica> razones, List<PasoCausal> caminoCausal) {
        this(estado, preguntaActual, nivel, destino, razones, caminoCausal, List.of());
    }

    /**
     * Construye un resultado EN_CURSO que espera la respuesta a {@code pregunta}.
     */
    public static ResultadoPaso pregunta(Nodo pregunta) {
        return new ResultadoPaso(Estado.EN_CURSO, pregunta, null, null, List.of(), List.of());
    }

    /**
     * Construye un resultado FINALIZADO con su nivel, destino, razones y camino
     * causal.
     */
    public static ResultadoPaso terminal(Nodo destino, NivelAlerta nivel,
            List<ReglaClinica> razones, List<PasoCausal> caminoCausal) {
        return terminal(destino, nivel, razones, caminoCausal, List.of());
    }

    /**
     * Construye un resultado FINALIZADO que además lleva las recomendaciones
     * alcanzadas durante el recorrido.
     */
    public static ResultadoPaso terminal(Nodo destino, NivelAlerta nivel,
            List<ReglaClinica> razones, List<PasoCausal> caminoCausal,
            List<Recomendacion> recomendaciones) {
        return new ResultadoPaso(Estado.FINALIZADO, null, nivel, destino,
                List.copyOf(razones), List.copyOf(caminoCausal), recomendaciones);
    }

    /** True si el recorrido terminó. */
    public boolean finalizado() {
        return estado == Estado.FINALIZADO;
    }
}
