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
 * Casos de prueba de la navegación sobre el grafo de normalización de base de
 * datos (1NF, 2NF, 3NF), cargado desde {@code static/ejemplo_grafo_norm.json} y
 * {@code static/ejemplo_reglas_norm.json}. Ejercita las tres capacidades
 * pedidas:
 *
 * <ol>
 * <li>terminación en el nodo rojo de mayor peso ({@code no_1nf}, URGENCIA);</li>
 * <li>pregunta de selección múltiple gobernada por una regla AND (gate): solo
 * se avanza a la segunda pregunta después de que R1 se dispara;</li>
 * <li>preguntas de selección única (SI/NO) hasta el desenlace.</li>
 * </ol>
 */
class MotorNavegacionNormalizacionTest {

    private GrafoClinico grafo;
    private BaseReglas base;
    private MotorNavegacion motor;

    @BeforeEach
    void cargar() throws IOException {
        grafo = GrafoClinicoParser.parseJsonToGrafoClinico(leer("static/ejemplo_grafo_norm.json"));
        base = BaseReglasParser.parseJsonToBaseReglas(leer("static/ejemplo_reglas_norm.json"), grafo);
        motor = new MotorNavegacion(new ServicioReglasImpl());
    }

    private static String leer(String ruta) throws IOException {
        return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> idsRegla(List<ReglaClinica> reglas) {
        return reglas.stream().map(ReglaClinica::getIdRegla).toList();
    }

    @Test
    void terminaEnElNodoRojoDeMayorPeso() {
        EstadoPaciente e = new EstadoPaciente("s1");
        Nodo q1nf = motor.iniciar(e, grafo).preguntaActual();
        assertEquals("q_1nf", q1nf.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, q1nf, "NINGUNA");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals("no_1nf", paso.destino().getId());
        assertEquals(3, paso.destino().getPeso(), "no_1nf es el rojo de mayor peso");
        assertEquals(List.of("R2"), idsRegla(paso.razones()));
    }

    @Test
    void seleccionMultipleDisparaReglaAndYContinuaALaSegundaPregunta() {
        EstadoPaciente e = new EstadoPaciente("s2");
        Nodo q1nf = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso trasProps = motor.paso(grafo, base, e, q1nf, List.of(
                "CELDA_UNICA", "CLAVE_PRIMARIA",
                "SIN_FILAS_O_COLUMNAS_DUPLICADAS", "VALOR_UNICO_POR_PAR"));

        // La regla gate R1 redirige a la segunda pregunta: el recorrido NO termina.
        assertFalse(trasProps.finalizado(), "la regla gate debe continuar el recorrido");
        assertEquals("q_2nf", trasProps.preguntaActual().getId());
        assertTrue(e.reglaDisparada("R1"), "solo se avanza tras dispararse la regla AND");

        // Se completa el recorrido: 2NF -> 3NF -> en 3NF (NORMAL).
        ResultadoPaso tras2nf = motor.paso(grafo, base, e, trasProps.preguntaActual(), "SI");
        assertEquals("q_3nf", tras2nf.preguntaActual().getId());

        ResultadoPaso tras3nf = motor.paso(grafo, base, e, tras2nf.preguntaActual(), "NO");
        assertTrue(tras3nf.finalizado());
        assertEquals(NivelAlerta.NORMAL, tras3nf.nivel());
        assertEquals("en_3nf", tras3nf.destino().getId());
    }

    @Test
    void seleccionMultipleParcialNoDisparaLaReglaYConservaTodasLasRespuestas() {
        EstadoPaciente e = new EstadoPaciente("s3");
        Nodo q1nf = motor.iniciar(e, grafo).preguntaActual();

        ResultadoPaso parcial = motor.paso(grafo, base, e, q1nf,
                List.of("CELDA_UNICA", "CLAVE_PRIMARIA"));

        assertFalse(parcial.finalizado());
        assertEquals("q_2nf", parcial.preguntaActual().getId());
        assertFalse(e.reglaDisparada("R1"), "sin las cuatro propiedades R1 no se dispara");
        assertEquals(List.of("CELDA_UNICA", "CLAVE_PRIMARIA"), e.getRespuestas("q_1nf"));
    }

    @Test
    void seleccionUnicaNo2nf() {
        EstadoPaciente e = new EstadoPaciente("s4");
        Nodo q1nf = motor.iniciar(e, grafo).preguntaActual();
        Nodo q2nf = motor.paso(grafo, base, e, q1nf, "CELDA_UNICA").preguntaActual();
        assertEquals("q_2nf", q2nf.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, q2nf, "NO");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals("no_2nf", paso.destino().getId());
        assertEquals(List.of("R3"), idsRegla(paso.razones()));
    }

    @Test
    void seleccionUnicaNo3nf() {
        EstadoPaciente e = new EstadoPaciente("s5");
        Nodo q1nf = motor.iniciar(e, grafo).preguntaActual();
        Nodo q2nf = motor.paso(grafo, base, e, q1nf, "CELDA_UNICA").preguntaActual();
        Nodo q3nf = motor.paso(grafo, base, e, q2nf, "SI").preguntaActual();
        assertEquals("q_3nf", q3nf.getId());

        ResultadoPaso paso = motor.paso(grafo, base, e, q3nf, "SI");

        assertTrue(paso.finalizado());
        assertEquals(NivelAlerta.URGENCIA, paso.nivel());
        assertEquals("no_3nf", paso.destino().getId());
        assertEquals(List.of("R4"), idsRegla(paso.razones()));
    }
}
