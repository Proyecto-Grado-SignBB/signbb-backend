package org.signbb.backend.reglas.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.servicios.ServicioReglasImpl;

/**
 * Evaluación de reglas con datos abstractos ({@code resp1/op1/op2/op3}) y los
 * TADs {@link EstadoPaciente} y {@link Nodo} simulados con Mockito: no depende
 * del dominio ni de la base de datos.
 *
 * <p>
 * Cubre una condición, varias condiciones (AND dentro de un caso), varios casos
 * (OR de la FND), combinación de varias opciones de la misma pregunta y la
 * resolución por severidad del servicio.
 */
class EvaluacionReglasAbstractaTest {

    private static final String RESP = "resp1";

    private final ServicioReglasImpl servicio = new ServicioReglasImpl();

    private static Nodo destino(String id, int peso) {
        Nodo destino = mock(Nodo.class);
        when(destino.getId()).thenReturn(id);
        when(destino.getPeso()).thenReturn(peso);
        return destino;
    }

    /** Estado simulado que ya marcó las respuestas indicadas en {@code resp1}. */
    private static EstadoPaciente estadoCon(String... respuestas) {
        EstadoPaciente estado = mock(EstadoPaciente.class);
        for (String r : respuestas) {
            when(estado.contieneRespuesta(RESP, r)).thenReturn(true);
        }
        return estado;
    }

    @Test
    void unaSolaCondicionSeCumpleConLaRespuestaMarcada() {
        ReglaClinica r1 = new ReglaClinica("R1", "abstracta",
                List.of(new Condicion(RESP, "op1")), destino("dest", 3), NivelAlerta.URGENCIA);

        assertTrue(r1.condicionesCumplidas(estadoCon("op1")));
        assertFalse(r1.condicionesCumplidas(estadoCon()));
        assertFalse(r1.condicionesCumplidas(estadoCon("op2")));
    }

    @Test
    void unCasoConVariasCondicionesRequiereTodas() {
        ReglaClinica r1 = new ReglaClinica("R1", "abstracta",
                List.of(new Condicion(RESP, "op1"), new Condicion(RESP, "op2"),
                        new Condicion(RESP, "op3")),
                destino("dest", 3), NivelAlerta.URGENCIA);

        assertTrue(r1.condicionesCumplidas(estadoCon("op1", "op2", "op3")));
    }

    @Test
    void unCasoConVariasCondicionesFallaSiFaltaUna() {
        ReglaClinica r1 = new ReglaClinica("R1", "abstracta",
                List.of(new Condicion(RESP, "op1"), new Condicion(RESP, "op2"),
                        new Condicion(RESP, "op3")),
                destino("dest", 3), NivelAlerta.URGENCIA);

        assertFalse(r1.condicionesCumplidas(estadoCon("op1", "op2")));
    }

    @Test
    void variosCasosSeCombinanConO() {
        ReglaClinica r1 = ReglaClinica.conCasos("R1", "abstracta",
                List.of(new Caso(List.of(new Condicion(RESP, "op1"))),
                        new Caso(List.of(new Condicion(RESP, "op2"), new Condicion(RESP, "op3")))),
                destino("dest", 3), NivelAlerta.URGENCIA);

        assertTrue(r1.condicionesCumplidas(estadoCon("op1")));
        assertTrue(r1.condicionesCumplidas(estadoCon("op2", "op3")));
        assertFalse(r1.condicionesCumplidas(estadoCon("op2")));
    }

    @Test
    void variasOpcionesDeLaMismaPreguntaSeCombinanConAnd() {
        ReglaClinica r1 = new ReglaClinica("R1", "abstracta",
                List.of(new Condicion(RESP, "op1"), new Condicion(RESP, "op2")),
                destino("dest", 2), NivelAlerta.CONSULTAR_MEDICO);

        assertTrue(r1.condicionesCumplidas(estadoCon("op1", "op2")));
        assertFalse(r1.condicionesCumplidas(estadoCon("op1")));
    }

    @Test
    void elServicioEligeElNivelMasSevero() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(new ReglaClinica("R_CONSULTA", "consulta",
                List.of(new Condicion(RESP, "op1")), destino("dest_consulta", 2),
                NivelAlerta.CONSULTAR_MEDICO));
        base.agregarRegla(new ReglaClinica("R_URGENCIA", "urgencia",
                List.of(new Condicion(RESP, "op2")), destino("dest_urgencia", 3),
                NivelAlerta.URGENCIA));

        ResultadoInferencia inferencia = servicio.evaluar(base, estadoCon("op1", "op2"));

        assertEquals(NivelAlerta.URGENCIA, inferencia.nivel());
        assertEquals("dest_urgencia", inferencia.destino().getId());
        assertEquals(1, inferencia.reglas().size());
        assertEquals("R_URGENCIA", inferencia.reglas().get(0).getIdRegla());
    }

    @Test
    void sinReglasActivadasElResultadoEsNormal() {
        BaseReglas base = new BaseReglas();
        base.agregarRegla(new ReglaClinica("R1", "abstracta",
                List.of(new Condicion(RESP, "op1")), destino("dest", 3), NivelAlerta.URGENCIA));

        ResultadoInferencia inferencia = servicio.evaluar(base, estadoCon());

        assertTrue(inferencia.esNormal());
        assertTrue(inferencia.reglas().isEmpty());
        assertNull(inferencia.destino());
    }
}
