package org.signbb.backend.navegacion.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.OpcionPendiente;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;

/**
 * Orden de la cola de opciones pendientes: terminal grave primero, luego
 * mayor peso del destino y, a igualdad, la opción más antigua.
 */
class OrdenColaTest {

    private GrafoClinico grafo;

    @BeforeEach
    void armarGrafo() {
        grafo = new GrafoClinico();
        // Pesos elegidos para que el peso solo NO baste: la urgencia pesa poco
        // y la recomendación pesa mucho.
        grafo.agregarNodo(new Nodo("urg", "urgencia", "Urgencia", 30));
        grafo.agregarNodo(new Nodo("cons", "consulta_medica", "Consulta", 20));
        grafo.agregarNodo(new Nodo("reco", "recomendacion", "Recomendación", 100));
        grafo.agregarNodo(new Nodo("pa", "pregunta", "Pregunta alta", 90));
        grafo.agregarNodo(new Nodo("pm", "pregunta", "Pregunta media", 60));
        grafo.agregarNodo(new Nodo("pm2", "pregunta", "Otra pregunta media", 60));
    }

    private static OpcionPendiente op(String destino, int orden) {
        return new OpcionPendiente("q", "opcion_" + destino, destino, orden, List.of());
    }

    private List<String> ordenar(OpcionPendiente... opciones) {
        List<OpcionPendiente> lista = new ArrayList<>(List.of(opciones));
        lista.sort(MotorNavegacion.ordenCola(grafo));
        return lista.stream().map(OpcionPendiente::idDestino).toList();
    }

    @Test
    void terminalesGravesVanPrimeroAunConMenorPeso() {
        assertEquals(List.of("urg", "cons", "reco", "pa"),
                ordenar(op("reco", 0), op("pa", 1), op("cons", 2), op("urg", 3)));
    }

    @Test
    void entrePreguntasYRecomendacionesDecideElPeso() {
        assertEquals(List.of("reco", "pa", "pm"),
                ordenar(op("pm", 0), op("pa", 1), op("reco", 2)));
    }

    @Test
    void aIgualPesoGanaLaMasAntigua() {
        assertEquals(List.of("pm2", "pm"), ordenar(op("pm", 5), op("pm2", 2)));
        assertEquals(List.of("pm", "pm2"), ordenar(op("pm", 1), op("pm2", 2)));
    }

    @Test
    void laAntiguedadNoSuperaALaGravedad() {
        // la más grave entró última y aun así va primero
        assertEquals("pa", ordenar(op("pm", 0), op("pm2", 1), op("pa", 2)).get(0));
    }

    @Test
    void mejorOpcionDevuelveLaPrimeraDelOrden() {
        List<OpcionPendiente> cola = List.of(op("pm", 0), op("cons", 1), op("pa", 2));
        assertEquals("cons", MotorNavegacion.mejorOpcion(cola, grafo).orElseThrow().idDestino());
        assertTrue(MotorNavegacion.mejorOpcion(List.of(), grafo).isEmpty());
    }

    @Test
    void severidadTerminalSoloDistingueUrgenciaYConsulta() {
        assertEquals(2, MotorNavegacion.severidadTerminal(grafo.obtenerNodoPorId("urg").orElseThrow()));
        assertEquals(1, MotorNavegacion.severidadTerminal(grafo.obtenerNodoPorId("cons").orElseThrow()));
        assertEquals(0, MotorNavegacion.severidadTerminal(grafo.obtenerNodoPorId("reco").orElseThrow()));
        assertEquals(0, MotorNavegacion.severidadTerminal(grafo.obtenerNodoPorId("pa").orElseThrow()));
    }

    @Test
    void unDestinoQueYaNoExisteFallaConUnMensajeClaro() {
        List<OpcionPendiente> cola = List.of(op("pm", 0), op("fantasma", 1));
        assertThrows(IllegalStateException.class, () -> MotorNavegacion.mejorOpcion(cola, grafo));
    }
}
