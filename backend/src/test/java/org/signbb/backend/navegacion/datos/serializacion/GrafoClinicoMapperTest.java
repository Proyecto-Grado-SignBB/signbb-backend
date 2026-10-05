package org.signbb.backend.navegacion.datos.serializacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.Respuesta;

/** Tests de ida y vuelta entre el TAD GrafoClinico y su documento JSON. */
class GrafoClinicoMapperTest {

  private static final String GRAFO_MINIMO = """
      {
        "graph": {
          "nodes": {
            "p1": { "label": "¿Pregunta?", "metadata": { "tipo": "pregunta", "peso": 1 } },
            "cm": { "label": "Consulta", "metadata": { "tipo": "consulta médica", "peso": 2 } }
          },
          "edges": [
            { "source": "p1", "relation": "SI", "target": "cm" }
          ]
        }
      }
      """;

  @Test
  void idaYVueltaDesdeElEjemploCompuesto() {
    GrafoClinico original = GrafoClinicoMapper.desde(ContenidoEjemplo.compuesto().grafo());
    GrafoClinico reconstruido = GrafoClinicoMapper.desde(GrafoClinicoMapper.desde(original));

    assertEquals(original.size(), reconstruido.size());
    assertEquals(
        original.obtenerNodoInicial().orElseThrow().getId(),
        reconstruido.obtenerNodoInicial().orElseThrow().getId());
    for (Nodo n : original.obtenerNodos()) {
      Nodo otro = reconstruido.obtenerNodoPorId(n.getId()).orElseThrow();
      assertEquals(n.getTipo(), otro.getTipo());
      assertEquals(n.getContenido(), otro.getContenido());
      assertEquals(n.getPeso(), otro.getPeso());
      assertEquals(n.tieneArchivos(), otro.tieneArchivos());
      assertEquals(n.getSrc(), otro.getSrc());
      assertEquals(n.getTipoInteraccion(), otro.getTipoInteraccion());

      List<Respuesta> esperadas = original.obtenerOpciones(n);
      List<Respuesta> actuales = reconstruido.obtenerOpciones(otro);
      assertEquals(esperadas, actuales);
    }
  }

  @Test
  void tadADocumentoReferenciaNodosPorId() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    GrafoClinicoJson doc = GrafoClinicoMapper.desde(g);

    assertEquals(2, doc.graph().nodes().size());
    assertEquals(1, doc.graph().edges().size());
    assertEquals("p1", doc.graph().preguntaInicial());
    assertEquals("¿Pregunta?", doc.graph().nodes().get("p1").label());
    assertEquals("pregunta", doc.graph().nodes().get("p1").metadata().tipo());
    assertEquals(1, doc.graph().nodes().get("p1").metadata().peso());
    assertEquals("terminal", doc.graph().nodes().get("cm").metadata().tipo());
    assertEquals("consulta_medica", doc.graph().nodes().get("cm").metadata().desenlace());
    assertEquals("SI", doc.graph().edges().get(0).relation());
    assertEquals("p1", doc.graph().edges().get(0).source());
    assertEquals("cm", doc.graph().edges().get(0).target());
  }

  @Test
  void normalizaTiposYCubreCamposAusentes() {
    GrafoClinico g = GrafoClinicoMapper.desde(GrafoClinicoParser.parseJsonToDocumento(GRAFO_MINIMO));

    assertEquals("consulta_medica", g.obtenerNodoPorId("cm").orElseThrow().getTipo());
    assertFalse(g.obtenerNodoPorId("p1").orElseThrow().tieneArchivos());
    assertTrue(g.obtenerNodoPorId("p1").orElseThrow().getSrc().isEmpty());
    assertEquals("p1", g.obtenerNodoInicial().orElseThrow().getId());
  }

  @Test
  void nodoInicialExplicitoSePreservaEnIdaYVuelta() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    g.setNodoInicial(g.obtenerNodoPorId("cm").orElseThrow());

    GrafoClinico reconstruido = GrafoClinicoMapper.desde(GrafoClinicoMapper.desde(g));
    assertEquals("cm", reconstruido.obtenerNodoInicial().orElseThrow().getId());
  }

  @Test
  void preguntaInicialInexistenteEsRechazada() {
    String json = GRAFO_MINIMO.replace("\"nodes\"", "\"preguntaInicial\": \"xx\", \"nodes\"");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }

  @Test
  void aristaANodoInexistenteEsRechazada() {
    String json = GRAFO_MINIMO.replace("\"target\": \"cm\"", "\"target\": \"no_existe\"");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }

  @Test
  void tipoInteraccionExplicitoSePreservaEnIdaYVuelta() {
    String json = GRAFO_MINIMO.replace(
        "\"tipo\": \"pregunta\", \"peso\": 1",
        "\"tipo\": \"pregunta\", \"tipoInteraccion\": \"SELECCION_MULTIPLE\", \"peso\": 1");
    GrafoClinico original = GrafoClinicoParser.parseJsonToGrafoClinico(json);
    assertEquals("SELECCION_MULTIPLE",
        original.obtenerNodoPorId("p1").orElseThrow().getTipoInteraccion());

    GrafoClinico reconstruido = GrafoClinicoMapper.desde(GrafoClinicoMapper.desde(original));
    assertEquals("SELECCION_MULTIPLE",
        reconstruido.obtenerNodoPorId("p1").orElseThrow().getTipoInteraccion());
  }

  @Test
  void sinTipoInteraccionQuedaNullParaQueRecorridoDTOInfiera() {
    GrafoClinico g = GrafoClinicoMapper.desde(GrafoClinicoParser.parseJsonToDocumento(GRAFO_MINIMO));

    assertNull(g.obtenerNodoPorId("p1").orElseThrow().getTipoInteraccion());
    assertNull(GrafoClinicoMapper.desde(g).graph().nodes().get("p1").metadata().tipoInteraccion());
  }

  @Test
  void tipoInteraccionInvalidoEsRechazado() {
    String json = GRAFO_MINIMO.replace(
        "\"peso\": 1", "\"peso\": 1, \"tipoInteraccion\": \"XYZ\"");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }

  @Test
  void nodoInicialDelFormatoPrevioSigueAceptado() {
    String json = GRAFO_MINIMO.replace("\"nodes\"", "\"nodoInicial\": \"cm\", \"nodes\"");

    assertEquals("cm", GrafoClinicoParser.parseJsonToGrafoClinico(json)
        .obtenerNodoInicial().orElseThrow().getId());
  }

  @Test
  void terminalTomaSuDesenlaceComoTipo() {
    String json = GRAFO_MINIMO.replace(
        "\"tipo\": \"consulta médica\"", "\"tipo\": \"terminal\", \"desenlace\": \"urgencia\"");

    assertEquals("urgencia",
        GrafoClinicoParser.parseJsonToGrafoClinico(json).obtenerNodoPorId("cm").orElseThrow().getTipo());
  }

  @Test
  void terminalConDesenlaceInvalidoEsRechazado() {
    String json = GRAFO_MINIMO.replace(
        "\"tipo\": \"consulta médica\"", "\"tipo\": \"terminal\", \"desenlace\": \"bloqueo\"");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }

  @Test
  void referenciaSinComponerEsRechazada() {
    String json = GRAFO_MINIMO.replace(
        "\"tipo\": \"consulta médica\"", "\"tipo\": \"referencia\", \"grafoId\": \"g\"");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }

  @Test
  void pesoFueraDeLaEscalaEsRechazado() {
    for (String peso : new String[] { "0", "4", "10" }) {
      String json = GRAFO_MINIMO.replace("\"peso\": 2", "\"peso\": " + peso);
      GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

      assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc), peso);
    }
  }

  @Test
  void pesoAusenteEsRechazado() {
    String json = GRAFO_MINIMO.replace(", \"peso\": 2", "");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }
}
