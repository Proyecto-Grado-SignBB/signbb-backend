package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests de persistencia de los documentos JSON: ida y vuelta por la columna
 * {@code estructura} (jsonb en PostgreSQL) y verificación de la siembra
 * inicial de {@link DatabaseInit}.
 */
@SpringBootTest
@Transactional
class PersistenciaEstructuraJsonTest {

  @Autowired
  private RepositorioGrafoClinico grafos;

  @Autowired
  private RepositorioBaseReglas bases;

  @PersistenceContext
  private EntityManager em;

  private static String leer(String ruta) {
    try (InputStream is = PersistenciaEstructuraJsonTest.class.getResourceAsStream(ruta)) {
      if (is == null) {
        throw new IllegalStateException("No se encontró " + ruta);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer " + ruta, e);
    }
  }

  @Test
  void grafoClinicoIdaYVueltaEnLaColumnaJson() {
    GrafoClinicoJson documento = GrafoClinicoParser.parseJsonToDocumento(
        leer("/static/ejemplo_subgrafo_piel_ictericia.json"));

    GrafoClinicoEntity entidad = new GrafoClinicoEntity();
    entidad.setEstructura(documento);
    grafos.saveAndFlush(entidad);
    em.clear();

    GrafoClinicoEntity recargado = grafos.findById(entidad.getId()).orElseThrow();
    assertEquals(documento, recargado.getEstructura());

    GrafoClinico tad = GrafoClinicoMapper.desde(recargado.getEstructura());
    assertEquals(5, tad.size());
    assertEquals("pregunta_coloracion", tad.obtenerNodoInicial().orElseThrow().getId());
  }

  @Test
  void baseReglasIdaYVueltaEnLaColumnaJson() {
    GrafoClinico grafo = GrafoClinicoParser.parseJsonToGrafoClinico(
        leer("/static/ejemplo_subgrafo_piel_ictericia.json"));
    BaseReglasJson documento = BaseReglasParser.parseJsonToDocumento(
        leer("/static/ejemplo_reglas_piel_ictericia.json"));

    BaseReglasEntity entidad = new BaseReglasEntity();
    entidad.setEstructura(documento);
    bases.saveAndFlush(entidad);
    em.clear();

    BaseReglasEntity recargado = bases.findById(entidad.getId()).orElseThrow();
    assertEquals(documento, recargado.getEstructura());

    BaseReglas tad = BaseReglasMapper.desde(recargado.getEstructura(), grafo);
    assertEquals("grafo_piel_ictericia", recargado.getEstructura().grafoId());
    assertEquals(3, tad.size());
    assertEquals("urgencia_inmediata", tad.obtenerRegla("R1").getNodoDestino().getId());
  }

  @Test
  void siembraInicialDejaElGrafoRaizConsultable() {
    assertTrue(grafos.findAll().stream()
        .anyMatch(f -> "grafo_raiz".equals(f.getEstructura().graph().id())),
        "DatabaseInit debe sembrar el grafo raíz");
    // Los subgrafos y las bases ya no se siembran; los crea el módulo de autoría.
  }
}
