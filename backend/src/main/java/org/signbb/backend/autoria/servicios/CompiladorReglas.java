package org.signbb.backend.autoria.servicios;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;

/**
 * Traduce las reglas de un borrador al documento de una base de reglas
 * ({@code BaseReglasJson}), el mismo formato de los {@code reglas_*.json} del
 * sistema en {@code static/grafo_sistema/}.
 *
 * <ul>
 * <li>Cada regla se serializa con su nombre, su FND de condiciones, su
 * {@code nodoDestino} y su {@code nivel}.</li>
 * <li>Los ids de nodos y el destino se emiten tal cual vienen del borrador
 * (locales); {@code CompositorGrafoClinico} los prefija al publicar un
 * subgrafo.</li>
 * <li>La metadata de cada regla lleva autor, institución y fechas del borrador
 * más la descripción de la regla.</li>
 * </ul>
 *
 * <p>
 * Supone un borrador sin avisos bloqueantes ({@link ValidadorReglas}).
 */
public final class CompiladorReglas {

    private CompiladorReglas() {
    }

    /**
     * Compila el borrador.
     *
     * @param borrador borrador con sus datos y sus reglas
     * @param fecha    fecha de la publicación, que va en la metadata
     */
    public static BaseReglasJson compilar(BorradorBaseReglaEntity borrador, Instant fecha) {
        Map<String, BaseReglasJson.ReglaJson> reglas = new LinkedHashMap<>();
        for (Map.Entry<String, BorradorRegla> e : borrador.getDocumento().reglas().entrySet()) {
            BorradorRegla regla = e.getValue();
            reglas.put(e.getKey(), new BaseReglasJson.ReglaJson(
                    regla.nombre(),
                    BaseReglasMapper.casosAJson(regla.casos()),
                    regla.nodoDestino(),
                    regla.nivel() == null ? null : regla.nivel().name(),
                    metadata(borrador, regla, fecha)));
        }
        return new BaseReglasJson(borrador.getGrafoId(), reglas);
    }

    /** Metadata de una regla; los campos vacíos no se escriben. */
    private static Map<String, Object> metadata(BorradorBaseReglaEntity borrador, BorradorRegla regla,
            Instant fecha) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        poner(metadata, "autor", borrador.getAutor());
        poner(metadata, "institucion", borrador.getInstitucion());
        Instant creado = borrador.getCreadoEn() != null ? borrador.getCreadoEn() : fecha;
        poner(metadata, "fechaCreacion", creado == null ? null : creado.toString());
        poner(metadata, "fechaPublicacion", fecha == null ? null : fecha.toString());
        poner(metadata, "descripcion", regla.descripcion());
        return metadata;
    }

    private static void poner(Map<String, Object> metadata, String clave, String valor) {
        if (valor != null && !valor.isBlank()) {
            metadata.put(clave, valor);
        }
    }
}
