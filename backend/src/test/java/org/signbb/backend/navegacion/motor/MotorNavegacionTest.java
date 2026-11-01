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
 * Tests del recorrido guiado del motor de navegación sobre el contenido
 * compuesto (grafo raíz + subgrafos de respiración, gastrointestinal y
 * actividad/color + sus reglas). Prueban el motor puro, sin Spring ni base de
 * datos, y ejercen las tres ramas del Algoritmo 3 (corto-circuito por regla,
 * avance por el grafo y cierre con el nivel del desenlace) cruzando de la raíz
 * al subgrafo, además de la validación de respuestas antes de registrarlas.
 * Volver atrás se prueba en {@link MotorNavegacionRetrocederTest}.
 */
class MotorNavegacionTest {

    private static final String RESPIRACION = "grafo_respiracion";

    /** Respuesta que descarta los signos de alarma y avanza a la alerta media. */
    private static final String NINGUNO = "No, ninguno de los anteriores";

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
        return CompositorGrafoClinico.prefijar(RESPIRACION, idLocal);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    /** Recorre raíz → respiración y devuelve la primera pregunta del subgrafo. */
    private Nodo hastaUrgencia(EstadoPaciente e) {
        Nodo inicial = motor.iniciar(e, grafo).preguntaActual();
        Nodo puente = motor.paso(grafo, base, e, inicial, "respiracion").preguntaActual();
        return motor.paso(grafo, base, e, puente, "iniciar").preguntaActual();
    }

    /** Encadena desde urgencia hasta la pregunta de signos de alerta media. */
    private Nodo hastaConsulta(EstadoPaciente e) {
        return motor.paso(grafo, base, e, hastaUrgencia(e), NINGUNO).preguntaActual();
    }

    /** Encadena hasta la pregunta de condiciones comunes. */
    private Nodo hastaNormal(EstadoPaciente e) {
        return motor.paso(grafo, base, e, hastaConsulta(e), NINGUNO).preguntaActual();
    }

    @Test
    void iniciarDevuelveLaPreguntaInicialDeLaRaiz() {
        ResultadoPaso paso = motor.iniciar(new EstadoPaciente("s"), grafo);

        assertFalse(paso.finalizado());
        assertEquals("pregunta_inicial", paso.preguntaActual().getId());
    }

    @Test
    void laRaizEnlazaConLaPreguntaInicialDelSubgrafo() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo inicial = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso trasCategoria = motor.paso(grafo, base, e, inicial, "respiracion");
        assertEquals("puente_respiracion", trasCategoria.preguntaActual().getId());

        ResultadoPaso trasIniciar = motor.paso(grafo, base, e,
                trasCategoria.preguntaActual(), "iniciar");
        assertFalse(trasIniciar.finalizado());
        assertEquals(id("q_resp_urgencia"), trasIniciar.preguntaActual().getId());
    }

    @Test
    void sinSignosDeAlarmaTerminaEnNormal() {
        EstadoPaciente e = new EstadoPaciente("s");

        ResultadoPaso paso = motor.paso(grafo, base, e, hastaNormal(e), "Estornudos o hipo");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertEquals(id("normal_1"), paso.destino().getId());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void unSignoDeAlarmaDisparaUrgencia() {
        EstadoPaciente e = new EstadoPaciente("s");

        ResultadoPaso paso = motor.paso(grafo, base, e, hastaUrgencia(e), "Quejido al respirar");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals(id("urgencia_1"), paso.destino().getId());
        assertEquals(List.of(id("R_RESP_URG")), idsRegla(paso.razones()));
    }

    @Test
    void unSignoDeAlertaMediaDisparaConsulta() {
        EstadoPaciente e = new EstadoPaciente("s");

        ResultadoPaso paso = motor.paso(grafo, base, e, hastaConsulta(e),
                "Presenta tos pero respira tranquilo");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.CONSULTAR_MEDICO, paso.nivel());
        assertEquals(id("consulta_1"), paso.destino().getId());
        assertEquals(List.of(id("R_RESP_CON")), idsRegla(paso.razones()));
    }

    @Test
    void laSeleccionMultipleSinReglasAvanzaPorLaMejorOpcionYEncolaElResto() {
        // Las categorías no disparan reglas: se sigue la primera marcada (todas
        // pesan igual) y la otra queda en la cola de pendientes.
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo inicial = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso paso = motor.paso(grafo, base, e, inicial,
                List.of("gastrointestinal", "respiracion"));

        assertFalse(paso.finalizado());
        assertEquals("puente_gastro", paso.preguntaActual().getId());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void respuestaQueNoEsOpcionSeRechazaSinTocarElEstado() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo urgencia = hastaUrgencia(e);

        assertThrows(IllegalArgumentException.class,
                () -> motor.paso(grafo, base, e, urgencia, "QUIZAS"));
        assertTrue(e.getRespuestas(urgencia.getId()).isEmpty());

        // El recorrido sigue en la misma pregunta y acepta una respuesta válida.
        assertEquals(id("q_resp_consulta"),
                motor.paso(grafo, base, e, urgencia, NINGUNO).preguntaActual().getId());
    }
}
