package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;

/** Tests del parser JSON de grafo y base de reglas. */
class JsonParserTest {

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

  private static String reglasJson(String nivel) {
    return """
        {
          "rules": {
            "R1": {
              "nombre": "prueba",
              "condiciones": [ { "idNodo": "p1", "respuesta": "SI" } ],
              "nodoDestino": "cm",
              "nivel": "%s"
            }
          }
        }
        """.formatted(nivel);
  }

  private static String leer(String ruta) {
    try (InputStream is = JsonParserTest.class.getResourceAsStream(ruta)) {
      if (is == null) {
        throw new IllegalStateException("No se encontró " + ruta);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer " + ruta, e);
    }
  }

  @Test
  void parseGrafoDeEjemplo() {
    GrafoClinico g = JsonParser.parseJsonToGrafoClinico(leer("/static/ejemplo_grafo.json"));

    assertEquals(11, g.size());
    assertEquals("inicio", g.obtenerNodoInicial().orElseThrow().getId());
    Nodo urgente = g.obtenerNodoPorId("consulta_medica_urgente").orElseThrow();
    assertEquals("consulta_medica", urgente.getTipo());
    assertEquals(100, urgente.getPeso());
  }

  @Test
  void parseReglasDeEjemploResuelveDestinosContraElGrafo() {
    GrafoClinico g = JsonParser.parseJsonToGrafoClinico(leer("/static/ejemplo_grafo.json"));
    BaseReglas base = JsonParser.parseJsonToBaseReglas(
        leer("/static/ejemplo_reglas.json"), g);

    assertEquals(4, base.size());

    ReglaClinica r1 = base.obtenerRegla("R1");
    assertEquals(NivelAlerta.URGENCIA, r1.getNivel());
    assertEquals("consulta_medica_urgente", r1.getNodoDestino().getId());
    assertEquals(2, r1.getCondiciones().size());
    assertEquals(new Condicion("pregunta_coloracion", "SI"), r1.getCondiciones().get(0));

    ReglaClinica r3 = base.obtenerRegla("R3");
    assertEquals(NivelAlerta.CONSULTAR_MEDICO, r3.getNivel());
    assertEquals("consulta_medica_prioritaria", r3.getNodoDestino().getId());
  }

  @Test
  void nivelNormalEsRechazado() {
    GrafoClinico g = JsonParser.parseJsonToGrafoClinico(GRAFO_MINIMO);

    assertThrows(IllegalArgumentException.class,
        () -> JsonParser.parseJsonToBaseReglas(reglasJson("NORMAL"), g));
  }

  @Test
  void nivelInvalidoEsRechazado() {
    GrafoClinico g = JsonParser.parseJsonToGrafoClinico(GRAFO_MINIMO);

    assertThrows(IllegalArgumentException.class,
        () -> JsonParser.parseJsonToBaseReglas(reglasJson("XYZ"), g));
  }

  @Test
  void nodoDestinoInexistenteEsRechazado() {
    GrafoClinico g = JsonParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    String json = reglasJson("URGENCIA").replace("\"cm\"", "\"no_existe\"");

    assertThrows(IllegalArgumentException.class,
        () -> JsonParser.parseJsonToBaseReglas(json, g));
  }
}
