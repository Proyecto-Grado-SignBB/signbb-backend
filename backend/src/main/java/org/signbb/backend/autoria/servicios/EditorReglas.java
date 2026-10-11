package org.signbb.backend.autoria.servicios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.NivelAlerta;

/**
 * Operaciones de edición del borrador de reglas. Cada una recibe el documento y
 * devuelve uno nuevo; no tiene estado ni toca la base de datos.
 *
 * <p>
 * Los ids los genera el editor con la {@code secuencia} del documento
 * ({@code R_N}) y no se reutilizan aunque se borre una regla. La validez de las
 * reglas la revisa {@link ValidadorReglas}.
 */
public final class EditorReglas {

    /**
     * Datos de una regla tal como los manda el panel de reglas.
     *
     * @param nombre      nombre de la regla
     * @param descripcion explicación para el médico, opcional
     * @param casos       FND (OR de casos, AND dentro de cada caso)
     * @param nivel       severidad al dispararse
     * @param nodoDestino id del nodo destino
     */
    public record DatosRegla(String nombre, String descripcion, List<Caso> casos, NivelAlerta nivel,
            String nodoDestino) {

        /** Normaliza los casos ausentes a una lista vacía. */
        public DatosRegla {
            casos = casos == null ? List.of() : List.copyOf(casos);
        }
    }

    private EditorReglas() {
    }

    /**
     * Agrega una regla nueva con el siguiente id libre.
     *
     * @throws EdicionInvalidaException si faltan los datos
     */
    public static DocumentoBaseRegla agregarRegla(DocumentoBaseRegla documento, DatosRegla datos) {
        if (datos == null) {
            throw new EdicionInvalidaException("Faltan los datos de la regla.");
        }
        int secuencia = documento.secuencia() + 1;
        String id = "R_" + secuencia;
        Map<String, BorradorRegla> reglas = new LinkedHashMap<>(documento.reglas());
        reglas.put(id, nueva(datos));
        return new DocumentoBaseRegla(secuencia, reglas);
    }

    /**
     * Edita una regla existente; conserva su id.
     *
     * @throws RecursoNoEncontradoException si la regla no existe
     * @throws EdicionInvalidaException     si faltan los datos
     */
    public static DocumentoBaseRegla editarRegla(DocumentoBaseRegla documento, String id, DatosRegla datos) {
        if (datos == null) {
            throw new EdicionInvalidaException("Faltan los datos de la regla.");
        }
        if (documento.regla(id).isEmpty()) {
            throw new RecursoNoEncontradoException("no hay una regla '" + id + "' en el borrador");
        }
        Map<String, BorradorRegla> reglas = new LinkedHashMap<>(documento.reglas());
        reglas.put(id, nueva(datos));
        return new DocumentoBaseRegla(documento.secuencia(), reglas);
    }

    /**
     * Elimina una regla.
     *
     * @throws RecursoNoEncontradoException si la regla no existe
     */
    public static DocumentoBaseRegla eliminarRegla(DocumentoBaseRegla documento, String id) {
        if (documento.regla(id).isEmpty()) {
            throw new RecursoNoEncontradoException("no hay una regla '" + id + "' en el borrador");
        }
        Map<String, BorradorRegla> reglas = new LinkedHashMap<>(documento.reglas());
        reglas.remove(id);
        return new DocumentoBaseRegla(documento.secuencia(), reglas);
    }

    private static BorradorRegla nueva(DatosRegla datos) {
        return new BorradorRegla(datos.nombre(), datos.descripcion(), datos.casos(), datos.nivel(),
                datos.nodoDestino());
    }
}
