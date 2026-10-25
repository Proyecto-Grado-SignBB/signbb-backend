package org.signbb.backend.observaciones.datos.entidades;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.signbb.backend.observaciones.modelo.SeleccionCuidador;
import org.signbb.backend.observaciones.modelo.TipoInteraccion;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fila de auditoría por cada respuesta de texto recibida del cuidador.
 * Distinta del TAD {@link SeleccionCuidador}: esta es la representación
 * persistida (mismo patrón que {@code navegacion.servicios.Nodo} frente a
 * {@code navegacion.modelo.Nodo}).
 *
 * <p>
 * Los índices los crea la migración {@code V3__integridad_e_indices.sql}; aquí
 * se declaran para que la entidad documente el esquema.
 */
@Entity
@Table(name = "observacion_entity", indexes = {
        @Index(name = "ix_observacion_recorrido", columnList = "recorrido_id, timestamp"),
        @Index(name = "ix_observacion_usuario", columnList = "usuario_id") })
@Getter
@NoArgsConstructor
public class ObservacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(nullable = false)
    private String recorridoId;

    /** Padre que respondió; {@code null} en las filas de antes de las cuentas. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    private String idNodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoInteraccion tipo;

    /** Opciones marcadas, en orden; {@code orden} es parte de la llave primaria de la tabla. */
    @ElementCollection
    @CollectionTable(name = "observacion_opciones", joinColumns = @JoinColumn(name = "observacion_id"))
    @OrderColumn(name = "orden")
    private List<String> opcionesSeleccionadas;

    @Column(nullable = false)
    private Instant timestamp;

    /** Construye la fila de auditoría a partir de la selección ya validada y del padre que respondió. */
    public static ObservacionEntity desde(SeleccionCuidador r, Long usuarioId) {
        ObservacionEntity e = new ObservacionEntity();
        e.recorridoId = r.getRecorridoId();
        e.usuarioId = usuarioId;
        e.idNodo = r.getIdNodo();
        e.tipo = r.getTipo();
        e.opcionesSeleccionadas = r.getOpcionesSeleccionadas();
        e.timestamp = r.getTimestamp();
        return e;
    }
}