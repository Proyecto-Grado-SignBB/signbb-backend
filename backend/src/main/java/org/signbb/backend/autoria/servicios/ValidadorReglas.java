package org.signbb.backend.autoria.servicios;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.Aviso.Codigo;
import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.reglas.modelo.NivelAlerta;

/**
 * Revisa las reglas de un borrador y devuelve los avisos para el médico, en
 * lenguaje humano.
 *
 * <p>
 * Es una validación estructural: nombre, condiciones, destino y nivel. La
 * existencia de los nodos, respuestas y destinos contra el grafo la comprueba
 * {@link org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper} al
 * componer y publicar, igual que en los subgrafos.
 */
public final class ValidadorReglas {

    private ValidadorReglas() {
    }

    /** Avisos de la base de reglas, en el orden de las reglas. */
    public static List<Aviso> validar(DocumentoBaseRegla documento) {
        List<Aviso> avisos = new ArrayList<>();
        if (documento == null) {
            return avisos;
        }
        for (Map.Entry<String, BorradorRegla> e : documento.reglas().entrySet()) {
            revisar(e.getKey(), e.getValue(), avisos);
        }
        return avisos;
    }

    /** True si algún aviso impide publicar. */
    public static boolean hayBloqueantes(List<Aviso> avisos) {
        return avisos.stream().anyMatch(Aviso::bloqueante);
    }

    private static void revisar(String idRegla, BorradorRegla regla, List<Aviso> avisos) {
        if (regla == null) {
            return;
        }
        String etiqueta = "\"" + nombre(idRegla, regla) + "\"";
        if (ValidadorLienzo.vacio(regla.nombre())) {
            avisos.add(new Aviso(Codigo.REGLA_SIN_NOMBRE, true,
                    "La regla '" + idRegla + "' no tiene nombre.", idRegla, null));
        }
        if (regla.casos().isEmpty()) {
            avisos.add(new Aviso(Codigo.REGLA_SIN_CONDICIONES, true,
                    etiqueta + " no tiene condiciones: agrega al menos una.", idRegla, null));
        }
        if (regla.nodoDestino() == null || regla.nodoDestino().isBlank()) {
            avisos.add(new Aviso(Codigo.REGLA_SIN_DESTINO, true,
                    etiqueta + " no dice a qué nodo lleva.", idRegla, null));
        }
        if (regla.nivel() == null || regla.nivel() == NivelAlerta.NORMAL) {
            avisos.add(new Aviso(Codigo.REGLA_NIVEL_NORMAL, true,
                    etiqueta + " necesita un nivel de alerta (consultar al médico o urgencia).",
                    idRegla, null));
        }
    }

    /** Nombre de la regla para los mensajes, o el id si no tiene. */
    private static String nombre(String idRegla, BorradorRegla regla) {
        return regla.nombre() != null && !regla.nombre().isBlank() ? regla.nombre() : idRegla;
    }
}
