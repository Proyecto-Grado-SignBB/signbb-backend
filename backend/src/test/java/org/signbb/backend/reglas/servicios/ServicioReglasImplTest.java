package org.signbb.backend.reglas.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.modelo.ResultadoInferencia;

/**
 * Verifica la resolución por severidad de {@link ServicioReglasImpl}
 * (Algoritmo 3, fase de resolución de conflicto), en particular el caso de
 * dos o más reglas de nivel máximo activándose a la vez.
 */
class ServicioReglasImplTest {

        private final ServicioReglasImpl servicio = new ServicioReglasImpl();

        private static Nodo destino(String id) {
                return new Nodo(id, "consulta_medica", "Contenido de " + id);
        }

        private static Nodo destino(String id, int peso) {
                return new Nodo(id, "consulta_medica", "Contenido de " + id, peso);
        }

        @Test
        void evaluarConDosReglasDeMismoNivelCombinaLasRazones() {
                Nodo destino = destino("consulta_medica_urgente");

                ReglaClinica r1 = new ReglaClinica(
                                "R1", "Ictericia prolongada",
                                List.of(new Condicion("pregunta_coloracion", "SI"),
                                                new Condicion("pregunta_tiempo", "MAS_DE_24_HORAS")),
                                destino, NivelAlerta.URGENCIA);

                ReglaClinica r2 = new ReglaClinica(
                                "R2", "Ictericia con letargo",
                                List.of(new Condicion("pregunta_coloracion", "SI"),
                                                new Condicion("pregunta_letargo", "SI")),
                                destino, NivelAlerta.URGENCIA);

                ReglaClinica r3 = new ReglaClinica(
                                "R3", "Ictericia con mala alimentación",
                                List.of(new Condicion("pregunta_alimentacion", "SI")),
                                destino("consulta_medica_prioritaria"), NivelAlerta.CONSULTAR_MEDICO);

                BaseReglas base = new BaseReglas();
                base.agregarRegla(r1);
                base.agregarRegla(r2);
                base.agregarRegla(r3);

                EstadoPaciente estado = new EstadoPaciente("sesion-test");
                estado.registrarRespuesta("pregunta_coloracion", "SI");
                estado.registrarRespuesta("pregunta_tiempo", "MAS_DE_24_HORAS");
                estado.registrarRespuesta("pregunta_letargo", "SI");
                // No se registra alimentación: R3 no debe activarse.

                ResultadoInferencia inferencia = servicio.evaluar(base, estado);

                assertEquals(NivelAlerta.URGENCIA, inferencia.nivel());
                assertEquals(2, inferencia.reglas().size());
                List<String> nombres = inferencia.reglas().stream().map(ReglaClinica::getNombre).toList();
                assertTrue(nombres.containsAll(List.of("Ictericia prolongada", "Ictericia con letargo")));
                assertEquals(destino, inferencia.destino());
        }

        @Test
        void evaluarSinReglasActivadasEsNormal() {
                BaseReglas base = new BaseReglas();
                base.agregarRegla(new ReglaClinica(
                                "R1", "Ictericia prolongada",
                                List.of(new Condicion("pregunta_coloracion", "SI")),
                                destino("consulta_medica_urgente"), NivelAlerta.URGENCIA));

                EstadoPaciente estado = new EstadoPaciente("sesion-test-2");
                // Sin respuestas registradas: ninguna condición se cumple.

                ResultadoInferencia inferencia = servicio.evaluar(base, estado);

                assertTrue(inferencia.esNormal());
                assertTrue(inferencia.reglas().isEmpty());
                assertNull(inferencia.destino());
        }

        @Test
        void evaluarConNivelesMixtosGanaElMasSevero() {
                Nodo destinoUrgencia = destino("consulta_medica_urgente");
                Nodo destinoConsulta = destino("consulta_medica_prioritaria");

                BaseReglas base = new BaseReglas();
                base.agregarRegla(new ReglaClinica(
                                "R2", "Ictericia con letargo",
                                List.of(new Condicion("pregunta_letargo", "SI")),
                                destinoUrgencia, NivelAlerta.URGENCIA));
                base.agregarRegla(new ReglaClinica(
                                "R3", "Ictericia con mala alimentación",
                                List.of(new Condicion("pregunta_alimentacion", "SI")),
                                destinoConsulta, NivelAlerta.CONSULTAR_MEDICO));

                EstadoPaciente estado = new EstadoPaciente("sesion-test-3");
                estado.registrarRespuesta("pregunta_letargo", "SI");
                estado.registrarRespuesta("pregunta_alimentacion", "SI");

                ResultadoInferencia inferencia = servicio.evaluar(base, estado);

                assertEquals(NivelAlerta.URGENCIA, inferencia.nivel());
                assertEquals(1, inferencia.reglas().size());
                assertEquals("Ictericia con letargo", inferencia.reglas().get(0).getNombre());
        }

        @Test
        void empateDeNivelGanaElDestinoDeMayorPeso() {
                // Dos reglas de igual nivel (URGENCIA) activándose a la vez, con
                // destinos de distinto peso: debe ganar el más crítico (mayor peso),
                // aunque se haya insertado después.
                Nodo destinoLeve = destino("consulta_leve", 10);
                Nodo destinoCritico = destino("consulta_critica", 90);

                BaseReglas base = new BaseReglas();
                base.agregarRegla(new ReglaClinica(
                                "R_LEVE", "Signo leve",
                                List.of(new Condicion("signo_leve", "SI")),
                                destinoLeve, NivelAlerta.URGENCIA));
                base.agregarRegla(new ReglaClinica(
                                "R_CRITICO", "Signo crítico",
                                List.of(new Condicion("signo_critico", "SI")),
                                destinoCritico, NivelAlerta.URGENCIA));

                EstadoPaciente estado = new EstadoPaciente("sesion-peso");
                estado.registrarRespuesta("signo_leve", "SI");
                estado.registrarRespuesta("signo_critico", "SI");

                ResultadoInferencia inferencia = servicio.evaluar(base, estado);

                assertEquals(NivelAlerta.URGENCIA, inferencia.nivel());
                assertEquals(2, inferencia.reglas().size());
                assertEquals("consulta_critica", inferencia.destino().getId());
        }
}