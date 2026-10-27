package org.signbb.backend.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.servicios.CompiladorReglas;
import org.signbb.backend.autoria.servicios.CompiladorSubgrafo;
import org.signbb.backend.autoria.servicios.ValidadorLienzo;
import org.signbb.backend.autoria.servicios.ValidadorReglas;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.springframework.core.io.ClassPathResource;

/** El JSON del seed se puede reconstruir y volver a compilar sin perder la estructura. */
class SeedGraphConverterTest {

    private static final List<String> GRAFOS = List.of(
            "grafo_respiracion", "grafo_gastro", "grafo_activo_color", "grafo_cabeza");

    private static String leer(String ruta) {
        try {
            return new ClassPathResource("seed/demo/" + ruta)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo leer " + ruta, e);
        }
    }

    @Test
    void cadaSubgrafoDelSeedSeReconstruyeYCompilaIgual() {
        for (String id : GRAFOS) {
            GrafoClinicoJson original = GrafoClinicoParser.parseJsonToDocumento(
                    leer(id + ".json"));
            BorradorSubgrafoEntity borrador = new BorradorSubgrafoEntity();
            borrador.setGrafoId(id);
            borrador.setNombre(original.graph().label());
            borrador.setCategoria("prueba");
            borrador.setDocumento(SeedGraphConverter.aLienzo(original));

            assertTrue(ValidadorLienzo.validar(borrador.getDocumento()).stream()
                    .noneMatch(Aviso::bloqueante), id + ": el lienzo tiene avisos bloqueantes");

            GrafoClinicoJson compilado = CompiladorSubgrafo.compilar(borrador, Instant.now());
            assertEquals(original.graph().nodes().keySet(), compilado.graph().nodes().keySet(),
                    id + ": cambiaron los nodos");
            assertEquals(original.graph().edges().size(), compilado.graph().edges().size(),
                    id + ": cambiaron las aristas");
            for (Map.Entry<String, GrafoClinicoJson.NodoJson> entry
                    : original.graph().nodes().entrySet()) {
                GrafoClinicoJson.NodoJson esperado = entry.getValue();
                GrafoClinicoJson.NodoJson obtenido = compilado.graph().nodes().get(entry.getKey());
                assertEquals(esperado.label(), obtenido.label(), id + ": cambió el texto de "
                        + entry.getKey());
                assertEquals(esperado.metadata().peso(), obtenido.metadata().peso(),
                        id + ": cambió el peso de " + entry.getKey());
            }
        }
    }

    @Test
    void cadaBaseDelSeedSeReconstruyeYCompilaIgual() {
        for (String id : GRAFOS) {
            BaseReglasJson original = BaseReglasParser.parseJsonToDocumento(
                    leer("reglas_" + id.replace("grafo_", "") + ".json"));
            var documento = SeedGraphConverter.aDocumentoReglas(original);

            assertTrue(ValidadorReglas.validar(documento).stream()
                    .noneMatch(Aviso::bloqueante), id + ": las reglas tienen avisos bloqueantes");

            var borrador = new org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity();
            borrador.setGrafoId(id);
            borrador.setDocumento(documento);
            BaseReglasJson compilado = CompiladorReglas.compilar(borrador, Instant.now());

            assertEquals(original.rules().keySet(), compilado.rules().keySet(),
                    id + ": cambiaron las reglas");
            for (String reglaId : original.rules().keySet()) {
                BaseReglasJson.ReglaJson esperada = original.rules().get(reglaId);
                BaseReglasJson.ReglaJson obtenida = compilado.rules().get(reglaId);
                assertEquals(esperada.nombre(), obtenida.nombre(), id + ": cambió el nombre");
                assertEquals(esperada.condiciones(), obtenida.condiciones(),
                        id + ": cambiaron las condiciones de " + reglaId);
                assertEquals(esperada.nodoDestino(), obtenida.nodoDestino(),
                        id + ": cambió el destino de " + reglaId);
                assertEquals(esperada.nivel(), obtenida.nivel(),
                        id + ": cambió el nivel de " + reglaId);
            }
        }
    }
}
