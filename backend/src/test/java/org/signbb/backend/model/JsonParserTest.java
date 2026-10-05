package org.signbb.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
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
            "p1": { "label": "¿Pregunta?", "metadata": { "tipo": "pregunta", "peso": 1 } },
            "cm": { "label": "Consulta", "metadata": { "tipo": "consulta médica", "peso": 2 } }
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
  void parseSubgrafoDeEjemploPorSiSolo() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(
        leer("/static/ejemplo_subgrafo_piel_ictericia.json"));

    assertEquals(5, g.size());
    assertEquals("pregunta_coloracion", g.obtenerNodoInicial().orElseThrow().getId());
    Nodo urgente = g.obtenerNodoPorId("urgencia_inmediata").orElseThrow();
    assertEquals("urgencia", urgente.getTipo());
    assertEquals(3, urgente.getPeso());
  }

  @Test
  void parseReglasDeEjemploResuelveDestinosContraSuSubgrafo() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(
        leer("/static/ejemplo_subgrafo_piel_ictericia.json"));
    BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(
        leer("/static/ejemplo_reglas_piel_ictericia.json"), g);

    assertEquals(3, base.size());

    ReglaClinica r1 = base.obtenerRegla("R1");
    assertEquals(NivelAlerta.URGENCIA, r1.getNivel());
    assertEquals("urgencia_inmediata", r1.getNodoDestino().getId());
    assertEquals(2, r1.getCondiciones().size());
    assertEquals(new Condicion("pregunta_coloracion", "SI"), r1.getCondiciones().get(0));

    ReglaClinica r3 = base.obtenerRegla("R3");
    assertEquals(NivelAlerta.CONSULTAR_MEDICO, r3.getNivel());
    assertEquals("consulta_prioritaria", r3.getNodoDestino().getId());
  }

  @Test
  void nivelNormalEsRechazado() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(reglasJson("NORMAL"), g));
  }

  @Test
  void nivelInvalidoEsRechazado() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(reglasJson("XYZ"), g));
  }

  @Test
  void nodoDestinoInexistenteEsRechazado() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    String json = reglasJson("URGENCIA").replace("\"cm\"", "\"no_existe\"");

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(json, g));
  }

  /** Grafo con las tres preguntas SI/NO que usan las reglas en FND. */
  private static final String GRAFO_SIGNOS = """
      {
        "graph": {
          "preguntaInicial": "coloracion",
          "nodes": {
            "coloracion": { "label": "¿Coloración?", "metadata": { "tipo": "pregunta", "peso": 2 } },
            "letargo": { "label": "¿Letargo?", "metadata": { "tipo": "pregunta", "peso": 2 } },
            "sangre": { "label": "¿Sangre?", "metadata": { "tipo": "pregunta", "peso": 2 } },
            "cm": { "label": "Consulta", "metadata": { "tipo": "terminal", "desenlace": "consulta_medica", "peso": 2 } }
          },
          "edges": [
            { "source": "coloracion", "relation": "SI", "target": "letargo" },
            { "source": "coloracion", "relation": "NO", "target": "letargo" },
            { "source": "letargo", "relation": "SI", "target": "sangre" },
            { "source": "letargo", "relation": "NO", "target": "sangre" },
            { "source": "sangre", "relation": "SI", "target": "cm" },
            { "source": "sangre", "relation": "NO", "target": "cm" }
          ]
        }
      }
      """;

  @Test
  void parseCondicionesConVariosCasosSeActivaConCualquiera() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_SIGNOS);
    // letargo=SI OR sangre=SI: dos casos de una condición cada uno.
    String json = """
        {
          "rules": {
            "R_OR": {
              "nombre": "cualquiera de dos signos",
              "condiciones": [
                [ { "idNodo": "letargo", "respuesta": "SI" } ],
                [ { "idNodo": "sangre", "respuesta": "SI" } ]
              ],
              "nodoDestino": "cm",
              "nivel": "URGENCIA"
            }
          }
        }
        """;
    BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(json, g);
    ReglaClinica regla = base.obtenerRegla("R_OR");

    EstadoPaciente soloLetargo = new EstadoPaciente("s1");
    soloLetargo.registrarRespuesta("letargo", "SI");
    assertTrue(regla.condicionesCumplidas(soloLetargo));

    EstadoPaciente ninguno = new EstadoPaciente("s2");
    ninguno.registrarRespuesta("letargo", "NO");
    ninguno.registrarRespuesta("sangre", "NO");
    assertFalse(regla.condicionesCumplidas(ninguno));
  }

  @Test
  void parseCondicionesEnFndDistribuyeLaConjuncion() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_SIGNOS);
    // coloracion=SI AND (letargo=SI OR sangre=SI), escrito en FND.
    String json = """
        {
          "rules": {
            "R_MIX": {
              "nombre": "coloración con signo grave",
              "condiciones": [
                [ { "idNodo": "coloracion", "respuesta": "SI" }, { "idNodo": "letargo", "respuesta": "SI" } ],
                [ { "idNodo": "coloracion", "respuesta": "SI" }, { "idNodo": "sangre", "respuesta": "SI" } ]
              ],
              "nodoDestino": "cm",
              "nivel": "URGENCIA"
            }
          }
        }
        """;
    BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(json, g);
    ReglaClinica regla = base.obtenerRegla("R_MIX");

    EstadoPaciente completo = new EstadoPaciente("s3");
    completo.registrarRespuesta("coloracion", "SI");
    completo.registrarRespuesta("sangre", "SI");
    assertTrue(regla.condicionesCumplidas(completo));

    EstadoPaciente sinRama = new EstadoPaciente("s4");
    sinRama.registrarRespuesta("coloracion", "SI");
    assertFalse(regla.condicionesCumplidas(sinRama));

    // getCondiciones aplana las hojas de todos los casos en orden.
    assertEquals(4, regla.getCondiciones().size());
  }

  @Test
  void formatoAndOrDeObjetoEsRechazado() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_SIGNOS);
    String json = """
        {
          "rules": {
            "R": {
              "condiciones": { "or": [ { "idNodo": "letargo", "respuesta": "SI" } ] },
              "nodoDestino": "cm",
              "nivel": "URGENCIA"
            }
          }
        }
        """;

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(json, g));
  }

  @Test
  void condicionSobreNodoInexistenteEsRechazada() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    String json = reglasJson("URGENCIA").replace("\"idNodo\": \"p1\"", "\"idNodo\": \"no_existe\"");

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(json, g));
  }

  @Test
  void condicionConRespuestaQueNoEsOpcionEsRechazada() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    String json = reglasJson("URGENCIA").replace("\"respuesta\": \"SI\"", "\"respuesta\": \"QUIZAS\"");

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(json, g));
  }

  @Test
  void condicionSobreTerminalEsRechazada() {
    GrafoClinico g = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    String json = reglasJson("URGENCIA").replace("\"idNodo\": \"p1\"", "\"idNodo\": \"cm\"");

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(json, g));
  }
}
