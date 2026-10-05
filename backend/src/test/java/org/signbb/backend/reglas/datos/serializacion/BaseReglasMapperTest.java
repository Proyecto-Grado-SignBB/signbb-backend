package org.signbb.backend.reglas.datos.serializacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.ReglaClinica;

/** Tests de ida y vuelta entre el TAD BaseReglas y su documento JSON. */
class BaseReglasMapperTest {

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

  private static final String REGLAS_MINIMAS = """
      {
        "rules": {
          "R1": {
            "nombre": "prueba",
            "condiciones": [ { "idNodo": "p1", "respuesta": "SI" } ],
            "nodoDestino": "cm",
            "nivel": "URGENCIA"
          }
        }
      }
      """;

  @Test
  void idaYVueltaDesdeElEjemploCompuesto() {
    CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
    GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
    BaseReglas original = BaseReglasMapper.desde(contenido.reglas(), grafo);
    BaseReglas reconstruido = BaseReglasMapper.desde(BaseReglasMapper.desde(original), grafo);

    assertEquals(original.size(), reconstruido.size());
    for (ReglaClinica r : original.todasLasReglas()) {
      ReglaClinica otra = reconstruido.obtenerRegla(r.getIdRegla());
      assertEquals(r.getNombre(), otra.getNombre());
      assertEquals(r.getNivel(), otra.getNivel());
      assertEquals(r.getNodoDestino().getId(), otra.getNodoDestino().getId());
      assertEquals(r.getCondiciones(), otra.getCondiciones());
    }
  }

  @Test
  void tadADocumentoReferenciaDestinoPorId() {
    GrafoClinico grafo = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(REGLAS_MINIMAS, grafo);
    BaseReglasJson doc = BaseReglasMapper.desde(base);

    assertEquals(1, doc.rules().size());
    assertEquals("prueba", doc.rules().get("R1").nombre());
    assertEquals("cm", doc.rules().get("R1").nodoDestino());
    assertEquals("URGENCIA", doc.rules().get("R1").nivel());
  }

  @Test
  void nodoDestinoColganteEsRechazado() {
    GrafoClinico grafo = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_MINIMO);
    String json = REGLAS_MINIMAS.replace("\"cm\"", "\"no_existe\"");

    assertThrows(IllegalArgumentException.class,
        () -> BaseReglasParser.parseJsonToBaseReglas(json, grafo));
  }
}
