package org.signbb.backend.mantenimiento;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decide qué versiones del contenido clínico se pueden borrar.
 *
 * <p>
 * Se conservan siempre las {@value #MINIMAS} versiones más recientes (la
 * vigente entre ellas) y, de las demás, las que fueron reemplazadas hace menos
 * de {@code GRACIA}. Una versión queda reemplazada cuando se guarda la
 * siguiente. La gracia es mayor que el TTL de las sesiones (24 h): un
 * recorrido empieza con la versión vigente y dura como máximo 24 h, así que
 * pasadas 25 h desde que dejó de ser la vigente ya nadie la usa.
 */
public final class PoliticaRetencionVersiones {

    /** Cantidad de versiones recientes que nunca se borran. */
    public static final int MINIMAS = 20;

    /** Tiempo que se sigue conservando una versión después de ser reemplazada. */
    public static final Duration GRACIA = Duration.ofHours(25);

    /**
     * Una versión del contenido.
     *
     * @param id       id de la versión (crece con cada publicación)
     * @param creadaEn cuándo se guardó
     */
    public record VersionConFecha(Long id, Instant creadaEn) {
    }

    private PoliticaRetencionVersiones() {
    }

    /**
     * Ids de las versiones que se pueden borrar.
     *
     * @param todas todas las versiones, en cualquier orden
     * @param ahora el momento de la limpieza
     */
    public static List<Long> aBorrar(List<VersionConFecha> todas, Instant ahora) {
        List<VersionConFecha> recientesPrimero = todas.stream()
                .sorted(Comparator.comparing(VersionConFecha::id).reversed())
                .toList();
        Instant limite = ahora.minus(GRACIA);
        List<Long> borrar = new ArrayList<>();
        for (int i = MINIMAS; i < recientesPrimero.size(); i++) {
            // La versión i la reemplazó la i-1, que es la siguiente en el tiempo.
            Instant reemplazadaEn = recientesPrimero.get(i - 1).creadaEn();
            if (reemplazadaEn.isBefore(limite)) {
                borrar.add(recientesPrimero.get(i).id());
            }
        }
        return borrar;
    }
}
