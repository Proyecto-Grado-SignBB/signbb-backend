package org.signbb.backend.mantenimiento;

import java.time.Duration;
import java.time.Instant;

import org.signbb.backend.seguridad.datos.repositorios.RepositorioLimiteIntentos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra los contadores de intentos cuya ventana ya terminó hace tiempo. No se
 * necesita para el funcionamiento, pero evita que la tabla crezca sin límite.
 */
@Component
public class LimpiadorLimites {

    /** Margen que se conserva después de vencer la ventana. */
    static final Duration MARGEN = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(LimpiadorLimites.class);

    private final RepositorioLimiteIntentos limites;

    /** Inyecta el repositorio de contadores. */
    public LimpiadorLimites(RepositorioLimiteIntentos limites) {
        this.limites = limites;
    }

    /**
     * Borra las filas vencidas antes del margen.
     *
     * @param ahora momento de la limpieza
     * @return cantidad de filas borradas
     */
    @Transactional
    public int limpiar(Instant ahora) {
        int borradas = limites.borrarVencidos(ahora.minus(MARGEN));
        log.info("Limpieza de límites: se borraron {} contadores vencidos.", borradas);
        return borradas;
    }
}
