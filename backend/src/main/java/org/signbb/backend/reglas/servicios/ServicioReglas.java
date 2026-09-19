package org.signbb.backend.reglas.servicios;

import java.util.List;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.modelo.ResultadoInferencia;

/**
 * Servicio del motor de reglas: encadenamiento hacia adelante sobre la base
 * de reglas y resolución de conflicto por severidad.
 */
public interface ServicioReglas {

    /** Reglas cuyas condiciones (AND) se cumplen todas en el estado dado. */
    public List<ReglaClinica> obtenerReglasActivadas(BaseReglas base, EstadoPaciente estado);

    /**
     * Evalúa el estado y devuelve nivel resultante, destino y razones combinadas.
     */
    public ResultadoInferencia evaluar(BaseReglas base, EstadoPaciente estado);
}
