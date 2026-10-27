package org.signbb.backend.seed;

import java.util.List;

/**
 * Manifiesto del seed de demo: los subgrafos que se publican y la categoría del
 * grafo raíz a la que pertenecen.
 */
public record SeedManifest(List<SubgrafoSeed> subgrafos) {

    /**
     * Un subgrafo del seed.
     *
     * @param grafo          JSON del subgrafo dentro de {@code seed/demo/}
     * @param reglas         JSON de su base de reglas dentro de {@code seed/demo/}
     * @param puenteId       pregunta puente del grafo raíz
     * @param categoria      nombre de la categoría del puente
     * @param etiquetaOpcion texto de la opción que verá la cuidadora
     */
    public record SubgrafoSeed(
            String grafo,
            String reglas,
            String puenteId,
            String categoria,
            String etiquetaOpcion) {
    }
}
