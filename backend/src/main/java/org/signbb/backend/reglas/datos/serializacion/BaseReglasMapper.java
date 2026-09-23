package org.signbb.backend.reglas.datos.serializacion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;

/**
 * Conversión bidireccional entre el TAD {@link BaseReglas} y su documento JSON
 * {@link BaseReglasJson}.
 *
 * <p>
 * El {@code nodoDestino} de cada regla se persiste como id (referencia) y se
 * resuelve contra el {@link GrafoClinico} correspondiente al reconstruir el
 * TAD, fallando de inmediato si el nodo no existe en el grafo.
 */
public final class BaseReglasMapper {

    private BaseReglasMapper() {
    }

    /** Reconstruye el TAD desde el documento JSON, resolviendo destinos contra el grafo. */
    public static BaseReglas desde(BaseReglasJson documento, GrafoClinico grafo) {
        if (documento == null || documento.rules() == null) {
            throw new IllegalArgumentException("el documento no tiene la clave 'rules'");
        }
        BaseReglas tad = new BaseReglas();
        for (Map.Entry<String, BaseReglasJson.ReglaJson> e : documento.rules().entrySet()) {
            BaseReglasJson.ReglaJson reglaJson = e.getValue();
            NivelAlerta nivel = aNivel(reglaJson.nivel(), e.getKey());
            Nodo destino = grafo.obtenerNodoPorId(reglaJson.nodoDestino())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "nodoDestino '" + reglaJson.nodoDestino()
                                    + "' no existe en el grafo"));
            tad.agregarRegla(new ReglaClinica(
                    e.getKey(),
                    reglaJson.nombre(),
                    reglaJson.condiciones(),
                    destino,
                    nivel));
        }
        return tad;
    }

    /** Serializa el TAD al documento JSON, referenciando destinos por id. */
    public static BaseReglasJson desde(BaseReglas tad) {
        Map<String, BaseReglasJson.ReglaJson> reglas = new LinkedHashMap<>();
        for (ReglaClinica regla : tad.todasLasReglas()) {
            reglas.put(regla.getIdRegla(), new BaseReglasJson.ReglaJson(
                    regla.getNombre(),
                    regla.getCondiciones(),
                    regla.getNodoDestino().getId(),
                    regla.getNivel().name(),
                    Map.of()));
        }
        return new BaseReglasJson(reglas);
    }

    /** Convierte el nivel del JSON al enum, rechazando NORMAL y valores inválidos. */
    private static NivelAlerta aNivel(String nivel, String idRegla) {
        if (nivel == null) {
            throw new IllegalArgumentException(
                    "la regla '" + idRegla + "' no tiene nivel");
        }
        NivelAlerta valor = NivelAlerta.valueOf(nivel);
        if (valor == NivelAlerta.NORMAL) {
            throw new IllegalArgumentException(
                    "la regla '" + idRegla + "' no puede tener nivel NORMAL");
        }
        return valor;
    }
}
