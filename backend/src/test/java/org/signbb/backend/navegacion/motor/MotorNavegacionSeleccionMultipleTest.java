package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Prueba de integración de la nueva sobrecarga
 * {@link MotorNavegacion#paso(GrafoClinico, BaseReglas, EstadoPaciente, Nodo, List)}
 * contra el grafo real de ictericia (el mismo JSON compartido para este
 * diseño), sin mocks: usa {@link GrafoClinicoParser} y
 * {@link BaseReglasParser} tal cual lo hace {@code ContextoClinico} al
 * arrancar.
 *
 * <p>
 * NOTA: escrita contra las firmas de {@code GrafoClinicoParser}/
 * {@code BaseReglasParser}, {@code GrafoClinico} y {@code MotorNavegacion}
 * vistas en el código compartido. Si
 * {@code BaseReglas}/{@code GrafoClinico} exponen algo distinto de lo
 * inferido aquí, ajustar la construcción de {@code GRAFO_JSON}/{@code
 * REGLAS_JSON} en consecuencia — el resto de la prueba no debería cambiar.
 */
class MotorNavegacionSeleccionMultipleTest {

    // Recorte del grafo de ictericia compartido: alcanza con el nodo inicial y
    // la primera pregunta (dos opciones, "SI"/"NO", con destinos de distinto
    // peso) para probar el desempate.
    private static final String GRAFO_JSON = """
            {
              "graph": {
                "directed": true,
                "type": "clinico",
                "label": "Evaluación de Ictericia en Bebés Prematuros",
                "nodes": {
                  "inicio": {
                    "label": "Evaluación de Ictericia",
                    "metadata": { "tipo": "inicio", "peso": 100, "archivos": false, "src": [] }
                  },
                  "pregunta_coloracion": {
                    "label": "¿Observa coloración amarilla en la piel o los ojos del bebé?",
                    "metadata": { "tipo": "pregunta", "peso": 95, "archivos": false, "src": [] }
                  },
                  "pregunta_tiempo": {
                    "label": "¿Desde hace cuánto tiempo presenta esta coloración?",
                    "metadata": { "tipo": "pregunta", "peso": 90, "archivos": false, "src": [] }
                  },
                  "recomendacion_tranquilidad": {
                    "label": "La coloración amarilla puede ser normal en los primeros días.",
                    "metadata": { "tipo": "recomendacion", "peso": 30, "archivos": false, "src": [] }
                  }
                },
                "edges": [
                  { "source": "inicio", "relation": "inicia_navegacion", "target": "pregunta_coloracion", "directed": true, "metadata": {} },
                  { "source": "pregunta_coloracion", "relation": "SI", "target": "pregunta_tiempo", "directed": true, "metadata": {} },
                  { "source": "pregunta_coloracion", "relation": "NO", "target": "recomendacion_tranquilidad", "directed": true, "metadata": {} }
                ]
              }
            }
            """;

    // Sin reglas: aísla la prueba a la lógica de navegación/peso, sin que el
    // encadenamiento hacia adelante interfiera.
    private static final String REGLAS_JSON = """
            { "rules": {} }
            """;

    @Test
    void seleccionMultipleAvanzaHaciaElDestinoDeMayorPeso() {
        GrafoClinico grafo = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_JSON);
        BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(REGLAS_JSON, grafo);
        MotorNavegacion motor = new MotorNavegacion(new ServicioReglasImpl());
        EstadoPaciente estado = new EstadoPaciente(UUID.randomUUID().toString());

        ResultadoPaso inicio = motor.iniciar(estado, grafo);
        assertFalse(inicio.finalizado());
        Nodo preguntaColoracion = inicio.preguntaActual();

        // El cuidador marca "SI" y "NO" a la vez (caso límite, para forzar el
        // desempate): "SI" lleva a pregunta_tiempo (peso 90), "NO" lleva a
        // recomendacion_tranquilidad (peso 30) — debe ganar "SI".
        ResultadoPaso resultado = motor.paso(grafo, base, estado, preguntaColoracion, List.of("NO", "SI"));

        assertFalse(resultado.finalizado(), "peso 90 (SI) debería ganarle a peso 30 (NO) y seguir la pregunta");
        assertEquals("pregunta_tiempo", resultado.preguntaActual().getId());
    }

    @Test
    void unaSolaRespuestaSeComportaIgualQueElCaminoExistente() {
        GrafoClinico grafo = GrafoClinicoParser.parseJsonToGrafoClinico(GRAFO_JSON);
        BaseReglas base = BaseReglasParser.parseJsonToBaseReglas(REGLAS_JSON, grafo);
        MotorNavegacion motor = new MotorNavegacion(new ServicioReglasImpl());
        EstadoPaciente estado = new EstadoPaciente(UUID.randomUUID().toString());

        ResultadoPaso inicio = motor.iniciar(estado, grafo);
        Nodo preguntaColoracion = inicio.preguntaActual();

        ResultadoPaso viaLista = motor.paso(grafo, base, estado, preguntaColoracion, List.of("NO"));

        assertEquals("recomendacion_tranquilidad", viaLista.destino().getId());
        assertEquals(30, viaLista.destino().getPeso()); // sanity check del fixture
    }
}