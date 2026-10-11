package org.signbb.backend.autoria.servicios;

import java.util.List;
import java.util.Map;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.modelo.BorradorRegla;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;

/**
 * Base de reglas de ejemplo de las pruebas, asociada al subgrafo de fiebre
 * ({@link LienzoFiebre}). Sus condiciones y destino usan los ids locales del
 * lienzo.
 */
final class BaseReglasFiebre {

    static final String ID = "R_1";

    private BaseReglasFiebre() {
    }

    /** Una regla: "Más de 39 °C" en la primera pregunta lleva a urgencias. */
    static DocumentoBaseRegla conUrgencia() {
        return EditorReglas.agregarRegla(DocumentoBaseRegla.vacio(), new EditorReglas.DatosRegla(
                "Fiebre alta", "Más de 39 grados es urgencia.",
                List.of(new Caso(List.of(new Condicion(LienzoFiebre.INICIO, "Más de 39 °C")))),
                NivelAlerta.URGENCIA, LienzoFiebre.URGENCIA));
    }

    /** Una regla a medio armar: sin nombre, sin condiciones, sin destino ni nivel. */
    static DocumentoBaseRegla incompleta() {
        return new DocumentoBaseRegla(1, Map.of("R_1", new BorradorRegla("", null, List.of(), null, null)));
    }

    /** Borrador de reglas de fiebre. */
    static BorradorBaseReglaEntity borrador(DocumentoBaseRegla documento) {
        BorradorBaseReglaEntity b = new BorradorBaseReglaEntity();
        b.setId(1L);
        b.setGrafoId("grafo_fiebre");
        b.setNombre("Reglas de fiebre");
        b.setAutor("Dra. Prueba");
        b.setInstitucion("Hospital de prueba");
        b.setDocumento(documento);
        b.setEstado(EstadoBorrador.BORRADOR);
        return b;
    }
}
