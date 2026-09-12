package org.signbb.backend.reglas.servicios;

import java.util.List;

import org.signbb.backend.model.BaseReglas;
import org.signbb.backend.model.EstadoPaciente;
import org.signbb.backend.model.ReglaClinica;

/**
 * Servicio del motor de reglas: encadenamiento hacia adelante sobre la base
 * de reglas y resolución de conflicto por severidad.
 */
public interface ServicioReglas {

    /** Reglas cuyas condiciones (AND) se cumplen todas en el estado dado. */
    public List<ReglaClinica> obtenerReglasActivadas(BaseReglas base, EstadoPaciente estado);

    /** Evalúa el estado y devuelve nivel resultante, destino y razones combinadas. */
    public ResultadoInferencia evaluar(BaseReglas base, EstadoPaciente estado);
}
