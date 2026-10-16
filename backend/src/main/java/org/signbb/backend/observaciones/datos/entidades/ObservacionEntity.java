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
import jakarta.persistence.JoinColumn;

import org.signbb.backend.observaciones.modelo.SeleccionCuidador;
import org.signbb.backend.observaciones.modelo.TipoInteraccion;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fila de auditoría por cada respuesta de texto recibida del cuidador.
 * Distinta del TAD {@link SeleccionCuidador}: esta es la representación
 * persistida (mismo patrón que {@code navegacion.servicios.Nodo} frente a
 * {@code navegacion.modelo.Nodo}).
 */
@Entity
@Getter
@NoArgsConstructor
public class ObservacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String recorridoId;

    /** Padre que respondió; {@code null} en las filas de antes de las cuentas. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    private String idNodo;

    @Enumerated(EnumType.STRING)
    private TipoInteraccion tipo;

    @ElementCollection
    @CollectionTable(name = "observacion_opciones", joinColumns = @JoinColumn(name = "observacion_id"))
    private List<String> opcionesSeleccionadas;

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