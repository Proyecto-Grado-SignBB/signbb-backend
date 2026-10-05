package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.PasoRecorrido;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.servicios.ServicioReglas;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Tests del recorrido guiado del motor de navegación sobre el contenido de
 * ejemplo compuesto (grafo raíz + subgrafo de ictericia + sus reglas). Prueban
 * el motor puro, sin Spring ni base de datos, y ejercen las tres ramas del
 * Algoritmo 3 (corto-circuito por regla, avance por el grafo y cierre con el
 * nivel del desenlace) cruzando de la raíz al subgrafo, además de la
 * validación de respuestas antes de registrarlas.
 */
class MotorNavegacionTest {

    private static final String ICTERICIA = "grafo_piel_ictericia";

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
        return CompositorGrafoClinico.prefijar(ICTERICIA, idLocal);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    /** Recorre raíz → piel → coloración amarilla y devuelve la primera pregunta del subgrafo. */
    private Nodo hastaColoracion(EstadoPaciente e) {
        Nodo inicial = motor.iniciar(e, grafo).preguntaActual();
        Nodo puente = motor.paso(grafo, base, e, inicial, "piel").preguntaActual();
        return motor.paso(grafo, base, e, puente, "coloracion_amarilla").preguntaActual();
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

        ResultadoPaso trasPiel = motor.paso(grafo, base, e, inicial, "piel");
        assertEquals("puente_piel", trasPiel.preguntaActual().getId());

        ResultadoPaso trasOpcion = motor.paso(grafo, base, e, trasPiel.preguntaActual(),
                "coloracion_amarilla");
        assertFalse(trasOpcion.finalizado());
        assertEquals(id("pregunta_coloracion"), trasOpcion.preguntaActual().getId());
    }

    @Test
    void sinColoracionTerminaEnNormal() {
        EstadoPaciente e = new EstadoPaciente("s");

        ResultadoPaso paso = motor.paso(grafo, base, e, hastaColoracion(e), "NO");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertEquals(id("recomendacion_lactancia"), paso.destino().getId());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void coloracionYSomnolenciaDisparaUrgenciaR1() {
        EstadoPaciente e = new EstadoPaciente("s");
        ResultadoPaso tras1 = motor.paso(grafo, base, e, hastaColoracion(e), "SI");
        assertFalse(tras1.finalizado(), "una sola condición no debe disparar ninguna regla");
        assertEquals(id("pregunta_signos"), tras1.preguntaActual().getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, tras1.preguntaActual(), List.of("SOMNOLENCIA"));

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals(id("urgencia_inmediata"), paso.destino().getId());
        assertEquals(List.of(id("R1")), idsRegla(paso.razones()));
    }

    @Test
    void comeMenosYAmarilloSeExtiendeEscalanAUrgenciaR2() {
        // Cada opción por separado lleva a consulta; juntas disparan R2 (URGENCIA)
        // y R3 (CONSULTAR_MEDICO), y gana el nivel máximo.
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();

        ResultadoPaso paso = motor.paso(grafo, base, e, signos,
                List.of("COME_MENOS", "AMARILLO_SE_EXTIENDE"));

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals(id("urgencia_inmediata"), paso.destino().getId());
        assertEquals(List.of(id("R2")), idsRegla(paso.razones()));
    }

    @Test
    void amarilloSeExtiendeDisparaConsultarMedicoR3() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();

        ResultadoPaso paso = motor.paso(grafo, base, e, signos, List.of("AMARILLO_SE_EXTIENDE"));

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.CONSULTAR_MEDICO, paso.nivel());
        assertEquals(id("consulta_prioritaria"), paso.destino().getId());
        assertEquals(List.of(id("R3")), idsRegla(paso.razones()));
    }

    @Test
    void ningunSignoLlegaATerminalSinAlarma() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();

        ResultadoPaso paso = motor.paso(grafo, base, e, signos, List.of("NINGUNO"));

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertEquals(id("recomendacion_lactancia"), paso.destino().getId());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void sinReglaLaSeleccionMultipleAvanzaPorLaOpcionMasGrave() {
        // NINGUNO (peso 1) y COME_MENOS (peso 2) no disparan reglas: se avanza
        // por la opción cuyo destino pesa más.
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();

        ResultadoPaso paso = motor.paso(grafo, base, e, signos, List.of("NINGUNO", "COME_MENOS"));

        assertTrue(paso.finalizado());
        assertEquals(id("consulta_prioritaria"), paso.destino().getId());
        // Sin regla, el nivel sale del desenlace del terminal (consulta_medica).
        assertEquals(NivelAlerta.CONSULTAR_MEDICO, paso.nivel());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void respuestaQueNoEsOpcionSeRechazaSinTocarElEstado() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = hastaColoracion(e);

        assertThrows(IllegalArgumentException.class,
                () -> motor.paso(grafo, base, e, coloracion, "QUIZAS"));
        assertTrue(e.getRespuestas(coloracion.getId()).isEmpty());

        // El recorrido sigue en la misma pregunta y acepta una respuesta válida.
        assertEquals(id("pregunta_signos"),
                motor.paso(grafo, base, e, coloracion, "SI").preguntaActual().getId());
    }

    @Test
    void variasRespuestasEnSeleccionUnicaSeRechazan() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = hastaColoracion(e);

        assertThrows(IllegalArgumentException.class,
                () -> motor.paso(grafo, base, e, coloracion, List.of("SI", "NO")));
        assertTrue(e.getRespuestas(coloracion.getId()).isEmpty());
    }

    @Test
    void respuestaRepetidaSeRechaza() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();

        assertThrows(IllegalArgumentException.class,
                () -> motor.paso(grafo, base, e, signos, List.of("COME_MENOS", "COME_MENOS")));
        assertTrue(e.getRespuestas(signos.getId()).isEmpty());
    }

    @Test
    void unTerminalNoAdmiteRespuestas() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo terminal = grafo.obtenerNodoPorId(id("recomendacion_lactancia")).orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> motor.paso(grafo, base, e, terminal, "SI"));
    }

    @Test
    void retrocederVuelveALaPreguntaAnteriorYBorraSuRespuesta() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo coloracion = hastaColoracion(e);
        Nodo signos = motor.paso(grafo, base, e, coloracion, "SI").preguntaActual();
        assertEquals(id("pregunta_signos"), signos.getId());

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertFalse(atras.finalizado());
        assertEquals(id("pregunta_coloracion"), atras.preguntaActual().getId());
        assertTrue(e.getRespuestas(coloracion.getId()).isEmpty());
        assertEquals(List.of("piel"), e.getRespuestas("pregunta_inicial"));

        // Con otra respuesta el recorrido toma el otro camino.
        ResultadoPaso paso = motor.paso(grafo, base, e, atras.preguntaActual(), "NO");
        assertEquals(id("recomendacion_lactancia"), paso.destino().getId());
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
    }

    @Test
    void retrocederTrasUnaReglaDeshaceSuDisparo() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();
        ResultadoPaso urgencia = motor.paso(grafo, base, e, signos, List.of("SOMNOLENCIA"));
        assertEquals(NivelAlerta.URGENCIA, urgencia.nivel());
        assertTrue(e.reglaDisparada(id("R1")));

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals(id("pregunta_signos"), atras.preguntaActual().getId());
        assertFalse(e.reglaDisparada(id("R1")), "la regla de la respuesta deshecha no debe seguir disparada");
        assertTrue(e.getRespuestas(signos.getId()).isEmpty());
        assertEquals(List.of("SI"), e.getRespuestas(id("pregunta_coloracion")));

        ResultadoPaso paso = motor.paso(grafo, base, e, atras.preguntaActual(), List.of("NINGUNO"));
        assertEquals(NivelAlerta.NORMAL, paso.nivel());
        assertTrue(paso.razones().isEmpty());
    }

    @Test
    void retrocederDosVecesVuelveDosPreguntas() {
        EstadoPaciente e = new EstadoPaciente("s");
        motor.paso(grafo, base, e, hastaColoracion(e), "SI");

        motor.retroceder(grafo, base, e);
        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals("puente_piel", atras.preguntaActual().getId());
        assertEquals(1, e.size());
    }

    @Test
    void retrocederSinRespuestasSeRechaza() {
        EstadoPaciente e = new EstadoPaciente("s");
        motor.iniciar(e, grafo);

        assertThrows(IllegalArgumentException.class, () -> motor.retroceder(grafo, base, e));
    }

    @Test
    void cadaRespuestaApilaUnPasoConLasReglasQueMarco() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();
        motor.paso(grafo, base, e, signos, List.of("SOMNOLENCIA"));

        List<PasoRecorrido> pasos = e.pasos();
        assertEquals(4, pasos.size());
        PasoRecorrido cima = pasos.get(3);
        assertEquals(id("pregunta_signos"), cima.idPregunta());
        assertEquals(List.of("SOMNOLENCIA"), cima.respuestas());
        assertEquals(List.of(id("R1")), cima.reglasMarcadas());
        // Las respuestas anteriores no marcaron ninguna regla.
        assertTrue(pasos.get(2).reglasMarcadas().isEmpty());
    }

    @Test
    void retrocederDesmarcaSoloLasReglasDeEsePasoYPermiteVolverADispararlas() {
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo signos = motor.paso(grafo, base, e, hastaColoracion(e), "SI").preguntaActual();
        motor.paso(grafo, base, e, signos, List.of("SOMNOLENCIA"));

        ResultadoPaso atras = motor.retroceder(grafo, base, e);
        assertFalse(e.reglaDisparada(id("R1")));
        assertEquals(3, e.pasos().size());

        ResultadoPaso otraVez = motor.paso(grafo, base, e, atras.preguntaActual(), List.of("SOMNOLENCIA"));
        assertEquals(NivelAlerta.URGENCIA, otraVez.nivel());
        assertEquals(List.of(id("R1")), idsRegla(otraVez.razones()));
    }

    @Test
    void retrocederNoReevaluaLasReglas() {
        ServicioReglas reglas = spy(new ServicioReglasImpl());
        MotorNavegacion conEspia = new MotorNavegacion(reglas);
        EstadoPaciente e = new EstadoPaciente("s");
        Nodo inicial = conEspia.iniciar(e, grafo).preguntaActual();
        Nodo puente = conEspia.paso(grafo, base, e, inicial, "piel").preguntaActual();
        Nodo coloracion = conEspia.paso(grafo, base, e, puente, "coloracion_amarilla").preguntaActual();
        conEspia.paso(grafo, base, e, coloracion, "SI");
        clearInvocations(reglas);

        ResultadoPaso atras = conEspia.retroceder(grafo, base, e);

        assertEquals(id("pregunta_coloracion"), atras.preguntaActual().getId());
        verify(reglas, never()).evaluar(any(), any());
    }

    @Test
    void sinPilaRetrocederReproduceElRecorrido() {
        // Estado armado a mano, como una sesión guardada antes de existir la pila.
        EstadoPaciente e = new EstadoPaciente("s");
        e.registrarRespuesta("pregunta_inicial", "piel");
        e.registrarRespuesta("puente_piel", "coloracion_amarilla");

        ResultadoPaso atras = motor.retroceder(grafo, base, e);

        assertEquals("puente_piel", atras.preguntaActual().getId());
        assertTrue(e.getRespuestas("puente_piel").isEmpty());
        // La reproducción deja la pila reconstruida.
        assertEquals(1, e.pasos().size());
        assertEquals("pregunta_inicial", e.pasos().get(0).idPregunta());
    }
}
