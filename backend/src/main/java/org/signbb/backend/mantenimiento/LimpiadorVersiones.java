package org.signbb.backend.mantenimiento;

import java.time.Instant;
import java.util.List;

import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.mantenimiento.PoliticaRetencionVersiones.VersionConFecha;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Borra las versiones viejas del contenido clínico según
 * {@link PoliticaRetencionVersiones}: de la base de datos, de la copia en
 * Redis y de la memoria del {@link ContextoClinico}.
 */
@Component
public class LimpiadorVersiones {

    private static final Logger log = LoggerFactory.getLogger(LimpiadorVersiones.class);

    private final RepositorioVersionContenido versiones;
    private final ResolvedorVersiones resolvedor;
    private final ContextoClinico contexto;

    /** Inyecta el repositorio de versiones, su cache y el contexto en memoria. */
    public LimpiadorVersiones(RepositorioVersionContenido versiones, ResolvedorVersiones resolvedor,
            ContextoClinico contexto) {
        this.versiones = versiones;
        this.resolvedor = resolvedor;
        this.contexto = contexto;
    }

    /**
     * Borra las versiones que ya no hacen falta.
     *
     * @param ahora el momento de la limpieza
     * @return ids de las versiones borradas (vacío si no había ninguna)
     */
    public List<Long> limpiar(Instant ahora) {
        List<VersionConFecha> todas = versiones.resumenes().stream()
                .map(r -> new VersionConFecha(r.getId(), r.getCreadaEn()))
                .toList();
        List<Long> borrar = PoliticaRetencionVersiones.aBorrar(todas, ahora);
        if (borrar.isEmpty()) {
            log.info("Limpieza de versiones: no hay versiones para borrar ({} guardadas).", todas.size());
            return List.of();
        }
        versiones.deleteAllByIdInBatch(borrar);
        resolvedor.olvidar(borrar);
        contexto.olvidar(borrar);
        log.info("Limpieza de versiones: se borraron {} de {} versiones ({}).", borrar.size(), todas.size(),
                borrar);
        return borrar;
    }
}
