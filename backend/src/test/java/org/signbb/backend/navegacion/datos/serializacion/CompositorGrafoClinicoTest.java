package org.signbb.backend.navegacion.datos.serializacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.reglas.modelo.BaseReglas;

/**
 * Tests de la composición del grafo raíz con los subgrafos: prefijos,
 * redirección a la pregunta inicial del subgrafo, omisión de referencias a
 * subgrafos no publicados, poda de puentes vacíos y unión de reglas.
 */
class CompositorGrafoClinicoTest {

  /**
   * Raíz con dos categorías: {@code a} tiene un subgrafo publicado
   * ({@code a1}) y otro que no existe; {@code b} solo apunta a un subgrafo que
   * no existe, así que su puente debe podarse.
   */
  private static final String RAIZ = """
      {
        "graph": {
          "id": "grafo_raiz",
          "preguntaInicial": "ini",
          "nodes": {
            "ini": { "label": "¿Qué observa?", "metadata": { "tipo": "pregunta", "peso": 1 } },
            "puente_a": { "label": "¿Qué en A?", "metadata": { "tipo": "pregunta", "peso": 1 } },
            "puente_b": { "label": "¿Qué en B?", "metadata": { "tipo": "pregunta", "peso": 1 } },
            "ref_a1": { "label": "A1", "metadata": { "tipo": "referencia", "grafoId": "a1", "peso": 1 } },
            "ref_a2": { "label": "A2", "metadata": { "tipo": "referencia", "grafoId": "a2", "peso": 1 } },
            "ref_b1": { "label": "B1", "metadata": { "tipo": "referencia", "grafoId": "b1", "peso": 1 } }
          },
          "edges": [
            { "source": "ini", "relation": "cat_a", "target": "puente_a" },
            { "source": "ini", "relation": "cat_b", "target": "puente_b" },
            { "source": "puente_a", "relation": "opc_a1", "target": "ref_a1" },
            { "source": "puente_a", "relation": "opc_a2", "target": "ref_a2" },
            { "source": "puente_b", "relation": "opc_b1", "target": "ref_b1" }
          ]
        }
      }
      """;

  private static final String SUBGRAFO_A1 = """
      {
        "graph": {
          "id": "a1",
          "preguntaInicial": "q",
          "nodes": {
            "q": { "label": "¿Signo?", "metadata": { "tipo": "pregunta", "peso": 2 } },
            "fin": { "label": "Urgencias", "metadata": { "tipo": "terminal", "desenlace": "urgencia", "peso": 3 } },
            "ok": { "label": "Tranquilo", "metadata": { "tipo": "terminal", "desenlace": "recomendacion", "peso": 1 } }
          },
          "edges": [
            { "source": "q", "relation": "SI", "target": "fin" },
            { "source": "q", "relation": "NO", "target": "ok" }
          ]
        }
      }
      """;

  private static String reglas(String grafoId, String idRegla, String idNodo, String destino) {
    return """
        {
          "grafoId": "%s",
          "rules": {
            "%s": {
              "nombre": "prueba",
              "condiciones": [ [ { "idNodo": "%s", "respuesta": "SI" } ] ],
              "nodoDestino": "%s",
              "nivel": "URGENCIA"
            }
          }
        }
        """.formatted(grafoId, idRegla, idNodo, destino);
  }

  private static GrafoClinicoJson doc(String json) {
    return GrafoClinicoParser.parseJsonToDocumento(json);
  }

  private static BaseReglasJson base(String json) {
    return BaseReglasParser.parseJsonToDocumento(json);
  }

  private static CompositorGrafoClinico.Contenido componerA1(BaseReglasJson... bases) {
    return CompositorGrafoClinico.componer(doc(RAIZ), List.of(doc(SUBGRAFO_A1)), List.of(bases));
  }

  private static boolean hayArista(GrafoClinicoJson grafo, String origen, String relacion, String destino) {
    return grafo.graph().edges().stream().anyMatch(a -> a.source().equals(origen)
        && a.relation().equals(relacion) && a.target().equals(destino));
  }

  @Test
  void prefijaLosIdsDelSubgrafoYQuitaLasReferencias() {
    GrafoClinicoJson grafo = componerA1().grafo();

    assertEquals(List.of("ini", "puente_a", "a1/q", "a1/fin", "a1/ok"),
        List.copyOf(grafo.graph().nodes().keySet()));
    assertTrue(hayArista(grafo, "a1/q", "SI", "a1/fin"));
    assertTrue(hayArista(grafo, "a1/q", "NO", "a1/ok"));
  }

  @Test
  void laOpcionDelPuenteLlevaALaPreguntaInicialDelSubgrafo() {
    GrafoClinicoJson grafo = componerA1().grafo();

    assertTrue(hayArista(grafo, "puente_a", "opc_a1", "a1/q"));
    assertEquals("ini", grafo.graph().preguntaInicial());
    assertEquals("grafo_raiz", grafo.graph().id());
  }

  @Test
  void laReferenciaASubgrafoNoPublicadoSeOmite() {
    GrafoClinicoJson grafo = componerA1().grafo();

    assertFalse(grafo.graph().edges().stream().anyMatch(a -> a.relation().equals("opc_a2")));
  }

  @Test
  void elPuenteSinSubgrafosSePodaConSuCategoria() {
    GrafoClinicoJson grafo = componerA1().grafo();

    assertFalse(grafo.graph().nodes().containsKey("puente_b"));
    assertFalse(grafo.graph().edges().stream().anyMatch(a -> a.relation().equals("cat_b")));
  }

  @Test
  void sinSubgrafosPublicadosFalla() {
    assertThrows(IllegalStateException.class,
        () -> CompositorGrafoClinico.componer(doc(RAIZ), List.of(), List.of()));
  }

  @Test
  void elResultadoConstruyeElTad() {
    CompositorGrafoClinico.Contenido contenido =
        componerA1(base(reglas("a1", "R1", "q", "fin")));

    GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
    BaseReglas reglas = BaseReglasMapper.desde(contenido.reglas(), grafo);

    assertEquals(5, grafo.size());
    assertEquals("ini", grafo.obtenerNodoInicial().orElseThrow().getId());
    assertEquals("urgencia", grafo.obtenerNodoPorId("a1/fin").orElseThrow().getTipo());
    assertEquals(1, reglas.size());
  }

  @Test
  void lasReglasDelSubgrafoSePrefijanSinTocarElOriginal() {
    BaseReglasJson original = base(reglas("a1", "R1", "q", "fin"));

    BaseReglasJson compuesta = componerA1(original).reglas();

    BaseReglasJson.ReglaJson regla = compuesta.rules().get("a1/R1");
    assertEquals("a1/fin", regla.nodoDestino());
    assertEquals("a1/q", regla.condiciones().get(0).get(0).get("idNodo").asText());
    assertEquals("q", original.rules().get("R1").condiciones().get(0).get(0).get("idNodo").asText());
  }

  @Test
  void lasReglasDeLaRaizNoSePrefijan() {
    BaseReglasJson compuesta =
        componerA1(base(reglas("grafo_raiz", "R_CRUZADA", "a1/q", "a1/fin"))).reglas();

    assertEquals("a1/fin", compuesta.rules().get("R_CRUZADA").nodoDestino());
  }

  @Test
  void lasReglasDeUnSubgrafoNoPublicadoSeOmiten() {
    BaseReglasJson compuesta = componerA1(base(reglas("a2", "R1", "q", "fin"))).reglas();

    assertTrue(compuesta.rules().isEmpty());
  }

  @Test
  void baseSinGrafoIdEsRechazada() {
    String sinGrafoId = reglas("a1", "R1", "q", "fin").replace("\"grafoId\": \"a1\",", "");

    assertThrows(IllegalArgumentException.class, () -> componerA1(base(sinGrafoId)));
  }

  @Test
  void subgrafosConElMismoIdSonRechazados() {
    assertThrows(IllegalArgumentException.class, () -> CompositorGrafoClinico.componer(
        doc(RAIZ), List.of(doc(SUBGRAFO_A1), doc(SUBGRAFO_A1)), List.of()));
  }

  @Test
  void subgrafoConReferenciaAnidadaEsRechazado() {
    String anidado = SUBGRAFO_A1.replace(
        "\"tipo\": \"terminal\", \"desenlace\": \"recomendacion\"",
        "\"tipo\": \"referencia\", \"grafoId\": \"otro\"");

    assertThrows(IllegalArgumentException.class, () -> CompositorGrafoClinico.componer(
        doc(RAIZ), List.of(doc(anidado)), List.of()));
  }

  @Test
  void pesoFueraDeLaEscalaEnUnSubgrafoSeRechazaAlConstruirElTad() {
    String pesoInvalido = SUBGRAFO_A1.replace("\"peso\": 3", "\"peso\": 100");
    CompositorGrafoClinico.Contenido contenido = CompositorGrafoClinico.componer(
        doc(RAIZ), List.of(doc(pesoInvalido)), List.of());

    assertThrows(IllegalArgumentException.class, () -> GrafoClinicoMapper.desde(contenido.grafo()));
  }

  @Test
  void elContenidoDeEjemploSeCompone() {
    CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
    GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
    BaseReglas reglas = BaseReglasMapper.desde(contenido.reglas(), grafo);

    // Solo la categoría piel tiene un subgrafo publicado (ictericia).
    assertEquals(7, grafo.size());
    assertEquals(3, reglas.size());
    assertEquals(List.of("piel"), grafo.obtenerOpciones(grafo.obtenerNodoInicial().orElseThrow())
        .stream().map(r -> r.getCondicion()).toList());
    assertTrue(grafo.rutaValida());
    assertTrue(grafo.validarCiclo());
  }
}
