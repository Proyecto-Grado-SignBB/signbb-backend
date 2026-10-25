package org.signbb.backend.mantenimiento;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea diaria de limpieza: primero las versiones viejas del contenido y
 * después las imágenes sin uso (así las imágenes que solo usaban versiones
 * recién borradas también se liberan). No tiene endpoint: corre sola.
 *
 * <p>
 * Si una de las dos partes falla, la otra se ejecuta igual y el error queda en
 * el log; se reintenta al día siguiente. Se puede apagar con
 * {@code signbb.limpieza.activa=false} y cambiar la hora con
 * {@code signbb.limpieza.cron} (por defecto, todos los días a las 3:30).
 */
@Component
@ConditionalOnProperty(name = "signbb.limpieza.activa", havingValue = "true", matchIfMissing = true)
public class LimpiezaProgramada {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaProgramada.class);

    private final LimpiadorVersiones versiones;
    private final LimpiadorImagenes imagenes;
    private final LimpiadorLimites limites;

    /** Inyecta los tres limpiadores. */
    public LimpiezaProgramada(LimpiadorVersiones versiones, LimpiadorImagenes imagenes,
            LimpiadorLimites limites) {
        this.versiones = versiones;
        this.imagenes = imagenes;
        this.limites = limites;
    }

    /** Ejecuta la limpieza completa. */
    @Scheduled(cron = "${signbb.limpieza.cron:0 30 3 * * *}")
    public void ejecutar() {
        Instant ahora = Instant.now();
        log.info("Limpieza diaria: empieza.");
        try {
            versiones.limpiar(ahora);
        } catch (RuntimeException e) {
            log.error("Limpieza de versiones falló: se reintenta mañana.", e);
        }
        try {
            imagenes.limpiar(ahora);
        } catch (RuntimeException e) {
            log.error("Limpieza de imágenes falló: se reintenta mañana.", e);
        }
        try {
            limites.limpiar(ahora);
        } catch (RuntimeException e) {
            log.error("Limpieza de límites falló: se reintenta mañana.", e);
        }
        log.info("Limpieza diaria: terminó.");
    }
}
