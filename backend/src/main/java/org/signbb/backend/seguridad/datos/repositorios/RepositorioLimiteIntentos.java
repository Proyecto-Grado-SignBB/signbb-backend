package org.signbb.backend.seguridad.datos.repositorios;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.signbb.backend.seguridad.datos.entidades.LimiteIntentosEntity;
import org.signbb.backend.seguridad.modelo.TipoLimite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Acceso a los contadores de intentos. La escritura usa un upsert atómico de
 * PostgreSQL para que dos peticiones concurrentes no creen dos filas para la
 * misma cuenta.
 */
@Repository
public interface RepositorioLimiteIntentos extends JpaRepository<LimiteIntentosEntity, Long> {

    /** Busca el contador de un tipo y una cuenta, si existe. */
    Optional<LimiteIntentosEntity> findByTipoAndClave(TipoLimite tipo, String clave);

    /**
     * Incrementa el contador de una cuenta. Si la fila no existe, la crea con
     * {@code contador = 1}; si la ventana venció, empieza de nuevo. Al llegar
     * al máximo fija el bloqueo y extiende la expiración.
     */
    @Modifying
    @Query(value = """
            INSERT INTO limite_intentos
                (tipo, clave, contador, ventana_inicio, bloqueado_hasta,
                 expira_en, actualizado_en)
            VALUES
                (:tipo, :clave, 1, CAST(:ahora AS timestamptz), NULL, CAST(:expira AS timestamptz), CAST(:ahora AS timestamptz))
            ON CONFLICT (tipo, clave) DO UPDATE SET
                contador = CASE
                    WHEN limite_intentos.expira_en <= CAST(:ahora AS timestamptz) THEN 1
                    ELSE limite_intentos.contador + 1
                END,
                ventana_inicio = CASE
                    WHEN limite_intentos.expira_en <= CAST(:ahora AS timestamptz) THEN CAST(:ahora AS timestamptz)
                    ELSE limite_intentos.ventana_inicio
                END,
                bloqueado_hasta = CASE
                    WHEN (CASE
                        WHEN limite_intentos.expira_en <= CAST(:ahora AS timestamptz) THEN 1
                        ELSE limite_intentos.contador + 1
                    END) >= :maximo
                    THEN CAST(:bloqueadoHasta AS timestamptz)
                    ELSE limite_intentos.bloqueado_hasta
                END,
                expira_en = CASE
                    WHEN (CASE
                        WHEN limite_intentos.expira_en <= CAST(:ahora AS timestamptz) THEN 1
                        ELSE limite_intentos.contador + 1
                    END) >= :maximo
                    THEN GREATEST(CAST(:expira AS timestamptz), CAST(:bloqueadoHasta AS timestamptz))
                    ELSE CAST(:expira AS timestamptz)
                END,
                actualizado_en = CAST(:ahora AS timestamptz)
            """, nativeQuery = true)
    void registrarIntento(@Param("tipo") String tipo,
            @Param("clave") String clave,
            @Param("ahora") OffsetDateTime ahora,
            @Param("expira") OffsetDateTime expira,
            @Param("maximo") int maximo,
            @Param("bloqueadoHasta") OffsetDateTime bloqueadoHasta);

    /** Borra el contador de una cuenta. */
    @Modifying
    @Query("delete from LimiteIntentosEntity l where l.tipo = :tipo and l.clave = :clave")
    void borrar(@Param("tipo") TipoLimite tipo, @Param("clave") String clave);

    /** Borra filas cuya ventana ya terminó hace tiempo. */
    @Modifying
    @Query("delete from LimiteIntentosEntity l where l.expiraEn < :limite")
    int borrarVencidos(@Param("limite") Instant limite);
}
