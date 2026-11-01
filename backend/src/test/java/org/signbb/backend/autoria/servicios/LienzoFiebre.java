package org.signbb.backend.autoria.servicios;

import java.util.ArrayList;
import java.util.List;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.autoria.servicios.EditorLienzo.DatosNodo;
import org.signbb.backend.autoria.servicios.EditorLienzo.DatosRespuesta;

/**
 * Lienzo de ejemplo de las pruebas, el subgrafo de fiebre del deck, armado
 * con {@link EditorLienzo} paso a paso:
 *
 * <pre>
 * pregunta_1 "¿Cuánto marca el termómetro?"
 *   r_2 "Menos de 38 °C" (LEVE)      ↪ va a pregunta_6 (pregunta existente)
 *   r_3 "Entre 38 y 39 °C" (MODERADA) → pregunta_6 "¿Está decaído o no quiere comer?"
 *                                         r_7 "Sí" (MODERADA) → conclusion_9 (CONSULTA_MEDICA)
 *                                         r_8 "No" (LEVE)     → conclusion_10 (RECOMENDACION)
 *   r_4 "Más de 39 °C" (GRAVE)        → conclusion_5 (URGENCIA)
 * </pre>
 */
final class LienzoFiebre {

    static final String INICIO = "pregunta_1";
    static final String MENOS_38 = "r_2";
    static final String ENTRE_38_39 = "r_3";
    static final String MAS_39 = "r_4";
    static final String URGENCIA = "conclusion_5";
    static final String DECAIDO = "pregunta_6";
    static final String SI = "r_7";
    static final String NO = "r_8";
    static final String CONSULTA = "conclusion_9";
    static final String RECOMENDACION = "conclusion_10";

    private LienzoFiebre() {
    }

    /** Pregunta con las respuestas dadas como pares texto, gravedad. */
    static DatosNodo pregunta(String texto, boolean varias, Object... respuestas) {
        List<DatosRespuesta> lista = new ArrayList<>();
        for (int i = 0; i < respuestas.length; i += 2) {
            lista.add(new DatosRespuesta(null, (String) respuestas[i], (Gravedad) respuestas[i + 1]));
        }
        return new DatosNodo(TipoNodo.PREGUNTA, texto, varias, null, lista);
    }

    static DatosNodo conclusion(Desenlace desenlace, String mensaje) {
        return new DatosNodo(TipoNodo.CONCLUSION, mensaje, false, desenlace, List.of());
    }

    /** Solo la primera pregunta, con sus tres "+" pendientes. */
    static DocumentoLienzo primeraPregunta() {
        return EditorLienzo.agregarNodo(DocumentoLienzo.vacio(), null, null,
                pregunta("¿Cuánto marca el termómetro?", false,
                        "Menos de 38 °C", Gravedad.LEVE,
                        "Entre 38 y 39 °C", Gravedad.MODERADA,
                        "Más de 39 °C", Gravedad.GRAVE));
    }

    /** El lienzo completo, sin avisos. */
    static DocumentoLienzo completo() {
        DocumentoLienzo d = primeraPregunta();
        d = EditorLienzo.agregarNodo(d, INICIO, MAS_39,
                conclusion(Desenlace.URGENCIA, "Lleve al bebé a urgencias ahora."));
        d = EditorLienzo.agregarNodo(d, INICIO, ENTRE_38_39,
                pregunta("¿Está decaído o no quiere comer?", false,
                        "Sí", Gravedad.MODERADA,
                        "No", Gravedad.LEVE));
        d = EditorLienzo.agregarNodo(d, DECAIDO, SI,
                conclusion(Desenlace.CONSULTA_MEDICA, "Pida una cita con el médico hoy."));
        d = EditorLienzo.agregarNodo(d, DECAIDO, NO,
                conclusion(Desenlace.RECOMENDACION, "Dele líquidos y vuelva a medir en una hora."));
        return EditorLienzo.enlazar(d, MENOS_38, DECAIDO);
    }

    /** Borrador del subgrafo de fiebre en la categoría neurológica (actividad, color, temperatura). */
    static BorradorSubgrafoEntity borrador(DocumentoLienzo documento) {
        BorradorSubgrafoEntity b = new BorradorSubgrafoEntity();
        b.setId(1L);
        b.setGrafoId("grafo_fiebre");
        b.setNombre("Fiebre");
        b.setPuenteId("puente_neuro");
        b.setCategoria("Actividad y color");
        b.setEtiquetaOpcion("Fiebre o temperatura alta");
        b.setAutor("Dra. Prueba");
        b.setInstitucion("Hospital de prueba");
        b.setDocumento(documento);
        b.setEstado(EstadoBorrador.BORRADOR);
        return b;
    }
}
