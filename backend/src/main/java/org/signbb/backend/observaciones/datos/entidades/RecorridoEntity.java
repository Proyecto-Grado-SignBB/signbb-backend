package org.signbb.backend.observaciones.datos.entidades;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Registro durable de un recorrido y de su dueño. La sesión vive en Redis y
 * vence en 24 h; esta fila queda para "Mis recorridos" y para saber de quién
 * es cada recorrido: solo el padre que lo inició puede verlo o seguirlo.
 */
@Entity
@Table(name = "recorrido")
@Data
public class RecorridoEntity {

    /** El mismo id (UUID) de la sesión en Redis. */
    @Id
    private String id;

    /** Cuenta del padre que lo inició. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** {@code EN_CURSO} o {@code FINALIZADO}, igual que en el {@code RecorridoDTO}. */
    @Column(nullable = false)
    private String estado;

    /** Nivel de alerta del resultado, o {@code null} si sigue en curso. */
    private String nivel;

    /** Mensaje de la conclusión, o {@code null} si sigue en curso. */
    @Column(length = 2000)
    private String mensaje;

    @Column(nullable = false)
    private Instant iniciadoEn;

    /** Cuándo terminó, o {@code null} si sigue en curso. */
    private Instant finalizadoEn;
}
