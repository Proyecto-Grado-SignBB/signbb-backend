package org.signbb.backend.autoria.modelo;

import java.util.List;

import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.NivelAlerta;

/**
 * Regla de un borrador de base de reglas, tal como la edita el médico.
 *
 * <p>
 * Reutiliza el TAD del dominio: los {@link Caso} son la forma normal
 * disjuntiva (OR de casos, AND dentro de cada caso) y {@code nodoDestino}
 * referencia un nodo del grafo al que pertenece la base, por id local
 * (el compositor lo prefija al publicar).
 *
 * @param nombre       texto descriptivo de la regla
 * @param descripcion  explicación para el médico, opcional
 * @param casos        FND de la regla; puede estar vacía mientras se edita
 * @param nivel        severidad al dispararse; {@link NivelAlerta#NORMAL} no es
 *                     válido para una regla
 * @param nodoDestino  id del nodo destino, o {@code null} mientras se edita
 */
public record BorradorRegla(
        String nombre,
        String descripcion,
        List<Caso> casos,
        NivelAlerta nivel,
        String nodoDestino) {

    /** Normaliza los casos ausentes a una lista vacía inmutable. */
    public BorradorRegla {
        casos = casos == null ? List.of() : List.copyOf(casos);
    }
}
