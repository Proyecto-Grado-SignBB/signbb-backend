package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;

import tools.jackson.databind.JsonNode;

/** Tests de {@link CompiladorReglas}: borrador de reglas → {@code BaseReglasJson}. */
class CompiladorReglasTest {

    private static final Instant FECHA = Instant.parse("2026-10-11T12:00:00Z");

    @Test
    void compilaElGrafoYLaReglaConSuFnd() {
        DocumentoBaseRegla documento = BaseReglasFiebre.conUrgencia();
        BaseReglasJson json = CompiladorReglas.compilar(BaseReglasFiebre.borrador(documento), FECHA);

        assertEquals("grafo_fiebre", json.grafoId());
        assertEquals(1, json.rules().size());

        BaseReglasJson.ReglaJson regla = json.rules().get("R_1");
        assertEquals("Fiebre alta", regla.nombre());
        assertEquals("conclusion_5", regla.nodoDestino());
        assertEquals("URGENCIA", regla.nivel());

        JsonNode condiciones = regla.condiciones();
        assertEquals("pregunta_1", condiciones.get(0).get(0).get("idNodo").asText());
        assertEquals("Más de 39 °C", condiciones.get(0).get(0).get("respuesta").asText());
    }

    @Test
    void laMetadataLlevaAutorYDescripcion() {
        DocumentoBaseRegla documento = BaseReglasFiebre.conUrgencia();
        BaseReglasJson json = CompiladorReglas.compilar(BaseReglasFiebre.borrador(documento), FECHA);

        var metadata = json.rules().get("R_1").metadata();
        assertEquals("Dra. Prueba", metadata.get("autor"));
        assertEquals("Hospital de prueba", metadata.get("institucion"));
        assertEquals("Más de 39 grados es urgencia.", metadata.get("descripcion"));
        assertEquals(FECHA.toString(), metadata.get("fechaPublicacion"));
    }

    @Test
    void variosCasosQuedanComoArregloDeArreglos() {
        DocumentoBaseRegla documento = BaseReglasFiebre.conUrgencia();
        BaseReglasJson json = CompiladorReglas.compilar(BaseReglasFiebre.borrador(documento), FECHA);
        JsonNode condiciones = json.rules().get("R_1").condiciones();
        assertEquals(1, condiciones.size(), "un caso");
        assertEquals(1, condiciones.get(0).size(), "una condición en el caso");
    }
}
