package org.signbb.backend.observaciones.datos.entidades;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Registro durable de un recorrido y de su dueño. La sesión vive en Redis y
 * vence tras 8 h sin actividad; esta fila queda para "Mis recorridos" y para
 * saber de quién es cada recorrido: solo el padre que lo inició puede verlo o
 * seguirlo.
 *
 * <p>
 * MODIFICADO (CU09): {@code estado} suma {@code VENCIDO} y
 * {@code ABANDONADO} (ver {@code ServicioRecorridosImpl}); la columna ya era
 * texto, así que el esquema no cambia.
 *
 * <p>
 * MODIFICADO (V3): la base restringe {@code estado} y {@code nivel} con
 * {@code check}, exige que {@code usuario_id} exista en {@code usuario} e
 * indexa el historial de cada padre ({@code ix_recorrido_usuario}).
 */
@Entity
@Table(name = "recorrido", indexes = @Index(name = "ix_recorrido_usuario", columnList = "usuario_id, iniciado_en desc"))
@Data
public class RecorridoEntity {

    /** El mismo id (UUID) de la sesión en Redis. */
    @Id
    private String id;

    /** Cuenta del padre que lo inició. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /**
     * {@code EN_CURSO} o {@code FINALIZADO}, igual que en el {@code RecorridoDTO};
     * {@code VENCIDO} si la sesión venció en curso y {@code ABANDONADO} si el
     * padre lo reinició.
     */
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
