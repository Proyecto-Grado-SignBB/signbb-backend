package org.signbb.backend.navegacion.datos.serializacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
            "p1": { "label": "¿Pregunta?", "metadata": { "tipo": "pregunta", "peso": 10 } },
            "cm": { "label": "Consulta", "metadata": { "tipo": "consulta médica", "peso": 50 } }
          },
          "edges": [
            { "source": "p1", "relation": "SI", "target": "cm" }
          ]
        }
      }
      """;

  private static String leer(String ruta) {
    try (InputStream is = GrafoClinicoMapperTest.class.getResourceAsStream(ruta)) {
      if (is == null) {
        throw new IllegalStateException("No se encontró " + ruta);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer " + ruta, e);
    }
  }

  @Test
  void idaYVueltaDesdeElEjemplo() {
    GrafoClinico original = GrafoClinicoParser.parseJsonToGrafoClinico(leer("/static/ejemplo_grafo.json"));
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
    assertEquals("p1", doc.graph().nodoInicial());
    assertEquals("¿Pregunta?", doc.graph().nodes().get("p1").label());
    assertEquals("pregunta", doc.graph().nodes().get("p1").metadata().tipo());
    assertEquals(10, doc.graph().nodes().get("p1").metadata().peso());
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
  void nodoInicialInexistenteEsRechazado() {
    String json = GRAFO_MINIMO.replace("\"nodes\"", "\"nodoInicial\": \"xx\", \"nodes\"");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }

  @Test
  void aristaANodoInexistenteEsRechazada() {
    String json = GRAFO_MINIMO.replace("\"target\": \"cm\"", "\"target\": \"no_existe\"");
    GrafoClinicoJson doc = GrafoClinicoParser.parseJsonToDocumento(json);

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(doc));
  }
}
