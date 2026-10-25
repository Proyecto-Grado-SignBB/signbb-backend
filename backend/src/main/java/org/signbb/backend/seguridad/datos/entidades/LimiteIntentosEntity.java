package org.signbb.backend.seguridad.datos.entidades;

import java.time.Instant;

import org.signbb.backend.seguridad.modelo.TipoLimite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contador persistente de intentos para un correo normalizado. Se usa para
 * que el límite de ingreso y el de registro no dependan de Redis.
 *
 * <p>
 * La clave es un hash del correo, no el correo en claro. El par
 * {@code (tipo, clave)} es único: una fila por tipo y cuenta.
 */
@Entity
@Table(name = "limite_intentos",
        uniqueConstraints = @UniqueConstraint(name = "uk_limite_intentos",
                columnNames = { "tipo", "clave" }))
@Getter
@Setter
@NoArgsConstructor
public class LimiteIntentosEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tipo de límite: ingreso o registro. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLimite tipo;

    /** Hash SHA-256 del correo normalizado. */
    @Column(nullable = false, length = 64)
    private String clave;

    /** Intentos acumulados en la ventana actual. */
    @Column(nullable = false)
    private int contador;

    /** Inicio de la ventana actual. */
    @Column(name = "ventana_inicio", nullable = false)
    private Instant ventanaInicio;

    /** Hasta cuándo está bloqueada la cuenta, o {@code null} si no lo está. */
    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    /** Cuándo se puede borrar la fila por vieja. */
    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    /** Última actualización del contador. */
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
