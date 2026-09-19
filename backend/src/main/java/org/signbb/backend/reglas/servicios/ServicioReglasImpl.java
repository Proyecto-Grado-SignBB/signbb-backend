package org.signbb.backend.reglas.servicios;

import java.util.ArrayList;
import java.util.List;

import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.modelo.ResultadoInferencia;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link ServicioReglas} que delega en {@link BaseReglas}
 * y aplica la jerarquía de {@link NivelAlerta}.
 */
@Service
public class ServicioReglasImpl implements ServicioReglas {

    /** Forward chaining: delega en {@code BaseReglas.reglasActivadas}. */
    @Override
    public List<ReglaClinica> obtenerReglasActivadas(BaseReglas base, EstadoPaciente estado) {
        return base.reglasActivadas(estado);
    }

    /**
     * Resolución por severidad: gana el nivel máximo; en empate se combinan
     * las razones y la primera regla por orden de inserción define el destino.
     */
    @Override
    public ResultadoInferencia evaluar(BaseReglas base, EstadoPaciente estado) {
        List<ReglaClinica> activadas = base.reglasActivadas(estado);
        if (activadas.isEmpty()) {
            return new ResultadoInferencia(null, NivelAlerta.NORMAL, List.of());
        }

        NivelAlerta nivelMax = NivelAlerta.NORMAL;
        for (ReglaClinica r : activadas) {
            if (r.getNivel().ordinal() > nivelMax.ordinal()) {
                nivelMax = r.getNivel();
            }
        }

        List<ReglaClinica> razones = new ArrayList<>();
        for (ReglaClinica r : activadas) {
            if (r.getNivel() == nivelMax) {
                razones.add(r);
            }
        }

        return new ResultadoInferencia(razones.get(0).getNodoDestino(), nivelMax, razones);
    }

}
