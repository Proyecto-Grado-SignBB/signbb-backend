package org.signbb.backend.crobservaciones.repositorios;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;

import org.signbb.backend.crobservaciones.modelo.SeleccionCuidador;
import org.signbb.backend.crobservaciones.modelo.TipoInteraccion;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fila de auditoría por cada respuesta de texto recibida del cuidador.
 * Distinta del TAD {@link SeleccionCuidador}: esta es la representación
 * persistida (mismo patrón que {@code navegacion.servicios.Nodo} frente a
 * {@code model.Nodo}).
 */
@Entity
@Getter
@NoArgsConstructor
public class ObservacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String recorridoId;
    private String idNodo;

    @Enumerated(EnumType.STRING)
    private TipoInteraccion tipo;

    @ElementCollection
    @CollectionTable(name = "observacion_opciones", joinColumns = @JoinColumn(name = "observacion_id"))
    private List<String> opcionesSeleccionadas;

    private Instant timestamp;

    /** Construye la fila de auditoría a partir de la selección ya validada. */
    public static ObservacionEntity desde(SeleccionCuidador r) {
        ObservacionEntity e = new ObservacionEntity();
        e.recorridoId = r.getRecorridoId();
        e.idNodo = r.getIdNodo();
        e.tipo = r.getTipo();
        e.opcionesSeleccionadas = r.getOpcionesSeleccionadas();
        e.timestamp = r.getTimestamp();
        return e;
    }
}
