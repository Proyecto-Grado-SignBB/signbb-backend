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
 * Casos de prueba de la navegación sobre el grafo de triaje de ciberseguridad
 * ({@code static/ejemplo_grafo_sec.json} y {@code static/ejemplo_reglas_sec.json}),
 * un ejemplo más complejo que el de normalización. Comprueba:
 *
 * <ol>
 * <li>con varias opciones marcadas se resuelve por la pregunta de mayor
 * prioridad (mayor peso del destino);</li>
 * <li>una regla que acumula respuestas de distintas preguntas dispara e
 * interrumpe la navegación;</li>
 * <li>una regla con varias condiciones AND y OR anidadas.</li>
 * </ol>
 */
class MotorNavegacionSeguridadTest {

    private GrafoClinico grafo;
    private BaseReglas base;
    private MotorNavegacion motor;

    @BeforeEach
    void cargar() throws IOException {
        grafo = GrafoClinicoParser.parseJsonToGrafoClinico(leer("static/ejemplo_grafo_sec.json"));
        base = BaseReglasParser.parseJsonToBaseReglas(leer("static/ejemplo_reglas_sec.json"), grafo);
        motor = new MotorNavegacion(new ServicioReglasImpl());
    }

    private static String leer(String ruta) throws IOException {
        return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    @Test
    void multiplesOpcionesResuelvenPorLaPreguntaDeMayorPrioridad() {
        EstadoPaciente e = new EstadoPaciente("s1");
        Nodo o = motor.iniciar(e, grafo).preguntaActual();
        assertEquals("O", o.getId());

        Nodo e1 = motor.paso(grafo, base, e, o, "Verificar correo").preguntaActual();
        assertEquals("E1", e1.getId());

        // Se marca primero la opción de MENOR prioridad: debe ganar igual la de mayor peso.
        ResultadoPaso paso = motor.paso(grafo, base, e, e1,
                List.of("Amenazas o advertencias", "Remitente desconocido"));

        assertFalse(paso.finalizado());
        assertEquals("E2", paso.preguntaActual().getId());
        assertEquals(List.of("Amenazas o advertencias", "Remitente desconocido"),
                e.getRespuestas("E1"));
    }

    @Test
    void empateDePesoGanaLaPrimeraOpcionMarcada() {
        EstadoPaciente e = new EstadoPaciente("s1b");
        Nodo o = motor.iniciar(e, grafo).preguntaActual();
        Nodo e1 = motor.paso(grafo, base, e, o, "Verificar correo").preguntaActual();

        // "Amenazas o advertencias" -> E3 (peso 1) y "No aplica" -> ET3 (peso 1):
        // con el mismo peso, el desempate es estable y gana la primera marcada.
        ResultadoPaso paso = motor.paso(grafo, base, e, e1,
                List.of("Amenazas o advertencias", "No aplica"));

        assertFalse(paso.finalizado());
        assertEquals("E3", paso.preguntaActual().getId());
    }

    @Test
    void reglaConAcumuladoDeVariasPreguntasInterrumpeElRecorrido() {
        EstadoPaciente e = new EstadoPaciente("s2");
        Nodo o = motor.iniciar(e, grafo).preguntaActual();
        Nodo e1 = motor.paso(grafo, base, e, o, "Verificar correo").preguntaActual();
        Nodo e2 = motor.paso(grafo, base, e, e1, "Remitente desconocido").preguntaActual();
        assertEquals("E2", e2.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, e2, "No");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals("ET1", paso.destino().getId());
        assertEquals(List.of("R1"), idsRegla(paso.razones()));
    }

    @Test
    void reglaConVariasCondicionesAndYOr() {
        EstadoPaciente e = new EstadoPaciente("s3");
        Nodo o = motor.iniciar(e, grafo).preguntaActual();
        Nodo a1 = motor.paso(grafo, base, e, o, "Verificar archivo sospechoso").preguntaActual();
        Nodo a3 = motor.paso(grafo, base, e, a1, "Enviado por un contacto").preguntaActual();
        assertEquals("A3", a3.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, a3, "Si");

        // R4 no aplica (el archivo no se descargó de un enlace); R5 sí, por sus OR.
        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals("AT1", paso.destino().getId());
        assertEquals(List.of("R5"), idsRegla(paso.razones()));
    }

    @Test
    void archivoDescargadoConDobleExtensionDisparaR4() {
        EstadoPaciente e = new EstadoPaciente("s4");
        Nodo o = motor.iniciar(e, grafo).preguntaActual();
        Nodo a1 = motor.paso(grafo, base, e, o, "Verificar archivo sospechoso").preguntaActual();
        Nodo a2 = motor.paso(grafo, base, e, a1, "Descargado de un enlace").preguntaActual();
        assertEquals("A2", a2.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, a2, "Si");

        assertTrue(paso.finalizado());
        assertEquals("AT1", paso.destino().getId());
        assertTrue(idsRegla(paso.razones()).contains("R4"));
    }

    @Test
    void caminosSinRiesgoCierranEnNormal() {
        EstadoPaciente e = new EstadoPaciente("s5");
        Nodo o = motor.iniciar(e, grafo).preguntaActual();
        Nodo e1 = motor.paso(grafo, base, e, o, "Verificar correo").preguntaActual();

        ResultadoPaso correo = motor.paso(grafo, base, e, e1, "No aplica");
        assertTrue(correo.finalizado());
        assertEquals(NivelAlerta.NORMAL, correo.nivel());
        assertEquals("ET3", correo.destino().getId());
        assertTrue(correo.razones().isEmpty());

        EstadoPaciente p = new EstadoPaciente("s6");
        Nodo op = motor.iniciar(p, grafo).preguntaActual();
        Nodo p1 = motor.paso(grafo, base, p, op, "Verificar contraseña").preguntaActual();
        Nodo p3 = motor.paso(grafo, base, p, p1, "Es la misma de otros sitios").preguntaActual();
        ResultadoPaso contrasena = motor.paso(grafo, base, p, p3, "Si");

        assertTrue(contrasena.finalizado());
        assertEquals(NivelAlerta.NORMAL, contrasena.nivel());
        assertEquals("PT3", contrasena.destino().getId());
    }
}
