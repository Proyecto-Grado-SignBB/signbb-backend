package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests del recorrido guiado del motor de navegación sobre el contenido real
 * del seed (raíz y subgrafos del demo). Prueban el motor puro, sin Spring ni
 * base de datos, y ejercen las tres ramas del Algoritmo 3: corto-circuito por
 * regla, avance por el grafo y cierre con el nivel del desenlace.
 */
class MotorNavegacionTest {

    private static final String ACTIVO = "grafo_activo_color";
    private static final String CATEGORIA = "Actividad y color";
    private static final String REVISAR = "Revisar actividad y color";
    private static final String SI = "Sí";
    private static final String MOVIMIENTO = "Movimiento y tono";
    private static final String LLANTO = "Llanto e irritabilidad";
    private static final String ALARMA = "Mucho más dormido / cuesta despertarlo";
    private static final String CONSULTA = "Más irritable/llora más, pero calma";
    private static final String NORMAL = "No";

    private GrafoClinico grafo;
    private BaseReglas base;
    private MotorNavegacion motor;

    @BeforeEach
    void cargar() {
        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        grafo = GrafoClinicoMapper.desde(contenido.grafo());
        base = BaseReglasMapper.desde(contenido.reglas(), grafo);
        motor = new MotorNavegacion(new ServicioReglasImpl());
    }

    private static String id(String idLocal) {
        return CompositorGrafoClinico.prefijar(ACTIVO, idLocal);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    /** Raíz → categoría → puente; devuelve la primera pregunta del subgrafo. */
    private Nodo hastaSubgrafo(EstadoPaciente e) {
        Nodo inicial = motor.iniciar(e, grafo).preguntaActual();
        Nodo puente = motor.paso(grafo, base, e, inicial, CATEGORIA).preguntaActual();
        return motor.paso(grafo, base, e, puente, REVISAR).preguntaActual();
    }

    /** Desde la puerta de actividad/color hasta la pregunta de tono. */
    private Nodo hastaUrgencia(EstadoPaciente e) {
        Nodo areas = motor.paso(grafo, base, e, hastaSubgrafo(e), SI).preguntaActual();
        return motor.paso(grafo, base, e, areas, MOVIMIENTO).preguntaActual();
    }

    /** Desde la puerta hasta la pregunta de llanto. */
    private Nodo hastaConsulta(EstadoPaciente e) {
        Nodo areas = motor.paso(grafo, base, e, hastaSubgrafo(e), SI).preguntaActual();
        return motor.paso(grafo, base, e, areas, LLANTO).preguntaActual();
    }

    @Test
    void iniciarDevuelveLaPreguntaInicialDeLaRaiz() {
        ResultadoPaso paso = motor.iniciar(new EstadoPaciente(), grafo);

        assertFalse(paso.finalizado());
        assertEquals("pregunta_inicial", paso.preguntaActual().getId());
    }

    @Test
    void laRaizEnlazaConLaPreguntaInicialDelSubgrafo() {
        EstadoPaciente e = new EstadoPaciente();
        Nodo inicial = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso trasCategoria = motor.paso(grafo, base, e, inicial, CATEGORIA);
        assertEquals("puente_neuro", trasCategoria.preguntaActual().getId());

        ResultadoPaso trasRevisar = motor.paso(grafo, base, e,
                trasCategoria.preguntaActual(), REVISAR);
        assertFalse(trasRevisar.finalizado());
        assertEquals(id("q_neuro_gate"), trasRevisar.preguntaActual().getId());
    }

    @Test
    void sinSignosDeAlarmaTerminaEnNormal() {
        EstadoPaciente e = new EstadoPaciente();

        ResultadoPaso paso = motor.paso(grafo, base, e, hastaSubgrafo(e), NORMAL);

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertEquals(id("T_NORMAL"), paso.destino().getId());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void unSignoDeAlarmaDisparaUrgencia() {
        EstadoPaciente e = new EstadoPaciente();

        ResultadoPaso paso = motor.paso(grafo, base, e, hastaUrgencia(e), ALARMA);

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals(id("T_URGENCIA"), paso.destino().getId());
        assertEquals(List.of(id("R_NEURO_URG")), idsRegla(paso.razones()));
    }

    @Test
    void unSignoDeAlertaMediaDisparaConsulta() {
        EstadoPaciente e = new EstadoPaciente();

        ResultadoPaso paso = motor.paso(grafo, base, e, hastaConsulta(e), CONSULTA);

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.CONSULTAR_MEDICO, paso.nivel());
        assertEquals(id("T_CONSULTA"), paso.destino().getId());
        assertEquals(List.of(id("R_NEURO_CON")), idsRegla(paso.razones()));
    }

    @Test
    void laSeleccionMultipleSinReglasAvanzaPorLaMejorOpcionYEncolaElResto() {
        EstadoPaciente e = new EstadoPaciente();
        Nodo inicial = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso paso = motor.paso(grafo, base, e, inicial,
                List.of("Gastrointestinal", CATEGORIA));

        assertFalse(paso.finalizado());
        assertEquals("puente_gastro", paso.preguntaActual().getId());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void respuestaQueNoEsOpcionSeRechazaSinTocarElEstado() {
        EstadoPaciente e = new EstadoPaciente();
        Nodo puerta = hastaSubgrafo(e);

        assertThrows(IllegalArgumentException.class,
                () -> motor.paso(grafo, base, e, puerta, "QUIZAS"));
        assertTrue(e.getRespuestas(puerta.getId()).isEmpty());

        ResultadoPaso paso = motor.paso(grafo, base, e, puerta, NORMAL);
        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
    }
}
