package org.signbb.backend.reglas.servicios;

import java.util.List;

import org.signbb.backend.model.NivelAlerta;
import org.signbb.backend.model.Nodo;
import org.signbb.backend.model.ReglaClinica;

/**
 * Resultado de evaluar la base de reglas contra el estado del paciente
 * (Algoritmo 3 del motor, fase de resolución de conflicto por severidad):
 * el destino de la regla ganadora {@code ρ*}, el nivel máximo {@code M} y
 * las reglas de nivel máximo {@code A*} cuyas razones se combinan.
 *
 * <p>Un nivel {@code NORMAL} con lista vacía indica que ninguna regla se
 * activó (el destino es {@code null} en ese caso).
 */
public record ResultadoInferencia(Nodo destino, NivelAlerta nivel, List<ReglaClinica> reglas) {

    public ResultadoInferencia {
        reglas = List.copyOf(reglas);
    }

    public boolean esNormal() {
        return nivel == NivelAlerta.NORMAL;
    }
}
