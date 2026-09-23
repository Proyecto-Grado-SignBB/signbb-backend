package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;
import org.springframework.core.io.ClassPathResource;

/**
 * Tests del recorrido guiado del motor de navegación sobre el grafo y las
 * reglas de ejemplo (ictericia). Prueban el motor puro, sin Spring ni base de
 * datos: cargan grafo + reglas con {@link GrafoClinicoParser} y
 * {@link BaseReglasParser} y ejercen las tres ramas del Algoritmo 3
 * (corto-circuito por regla, avance por el grafo y NORMAL por defecto).
 */
class MotorNavegacionTest {

    private GrafoClinico grafo;
    private BaseReglas base;
    private MotorNavegacion motor;

    @BeforeEach
    void cargar() throws IOException {
        grafo = GrafoClinicoParser.parseJsonToGrafoClinico(leer("static/ejemplo_grafo.json"));
        base = BaseReglasParser.parseJsonToBaseReglas(leer("static/ejemplo_reglas.json"), grafo);
        motor = new MotorNavegacion(new ServicioReglasImpl());
    }

    private static String leer(String ruta) throws IOException {
        return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    @Test
    void iniciarAutoAvanzaDesdeInicioHastaLaPrimeraPregunta() {
        ResultadoPaso paso = motor.iniciar(new EstadoPaciente("s"), grafo);

        assertFalse(paso.finalizado());
        assertEquals("pregunta_coloracion", paso.preguntaActual().getId());
    }

    @Test
    void sinColoracionTerminaEnNormal() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso paso = motor.paso(grafo, base, e, coloracion, "NO");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertEquals("recomendacion_tranquilidad", paso.destino().getId());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void coloracionYMasDe24HorasDisparaUrgenciaR1() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso tras1 = motor.paso(grafo, base, e, coloracion, "SI");
        assertFalse(tras1.finalizado(), "una sola condición no debe disparar ninguna regla");
        assertEquals("pregunta_signos", tras1.preguntaActual().getId());

        // La pregunta de signos es de selección múltiple: sin regla para esta
        // opción, avanza por el grafo hacia la pregunta de tiempo.
        ResultadoPaso trasSignos = motor.paso(grafo, base, e, tras1.preguntaActual(), "AMARILLO_SE_EXTIENDE");
        assertFalse(trasSignos.finalizado());
        assertEquals("pregunta_tiempo", trasSignos.preguntaActual().getId());

        ResultadoPaso tras2 = motor.paso(grafo, base, e, trasSignos.preguntaActual(), "MAS_DE_24_HORAS");

        assertTrue(tras2.finalizado());
        assertEquals(NivelAlerta.URGENCIA, tras2.nivel());
        assertEquals("consulta_medica_urgente", tras2.destino().getId());
        assertEquals(List.of("R1"), idsRegla(tras2.razones()));
    }

    @Test
    void coloracionMenos24YAlimentacionDisparaConsultarMedicoR3() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = motor.iniciar(e, grafo).preguntaActual();
        Nodo signos = motor.paso(grafo, base, e, coloracion, "SI").preguntaActual();
        assertEquals("pregunta_signos", signos.getId());
        Nodo tiempo = motor.paso(grafo, base, e, signos, "AMARILLO_SE_EXTIENDE").preguntaActual();
        Nodo alimentacion = motor.paso(grafo, base, e, tiempo, "MENOS_DE_24_HORAS").preguntaActual();
        assertEquals("pregunta_alimentacion", alimentacion.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, alimentacion, "SI");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.CONSULTAR_MEDICO, paso.nivel());
        assertEquals("consulta_medica_prioritaria", paso.destino().getId());
        assertEquals(List.of("R3"), idsRegla(paso.razones()));
    }

    @Test
    void empateUrgenciaCombinaLasRazones() {
        // Se pre-siembra el estado para que la última respuesta dispare R1 y R2
        // a la vez (ambas URGENCIA): en un recorrido natural R1 cortaría antes.
        EstadoPaciente e = new EstadoPaciente("s");
        e.registrarRespuesta("pregunta_coloracion", "SI");
        e.registrarRespuesta("pregunta_letargo", "SI");
        Nodo tiempo = grafo.obtenerNodoPorId("pregunta_tiempo").orElseThrow();

        ResultadoPaso paso = motor.paso(grafo, base, e, tiempo, "MAS_DE_24_HORAS");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals(List.of("R1", "R2"), idsRegla(paso.razones()));
        assertEquals("consulta_medica_urgente", paso.destino().getId());
    }

    @Test
    void recorridoNormalCompletoLlegaATerminalSinAlarma() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = motor.iniciar(e, grafo).preguntaActual();
        Nodo signos = motor.paso(grafo, base, e, coloracion, "SI").preguntaActual();
        Nodo tiempo = motor.paso(grafo, base, e, signos, "AMARILLO_SE_EXTIENDE").preguntaActual();
        Nodo alimentacion = motor.paso(grafo, base, e, tiempo, "MENOS_DE_24_HORAS").preguntaActual();
        Nodo letargo = motor.paso(grafo, base, e, alimentacion, "NO").preguntaActual();
        assertEquals("pregunta_letargo", letargo.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, letargo, "NO");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertEquals("recomendacion_observacion", paso.destino().getId());
        assertTrue(paso.razones().isEmpty());
    }
}
