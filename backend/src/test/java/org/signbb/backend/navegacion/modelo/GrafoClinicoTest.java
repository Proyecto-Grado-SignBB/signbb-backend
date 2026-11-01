package org.signbb.backend.navegacion.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests del TAD GrafoClinico (migrados de tests/test_grafo_clinico.py). */
class GrafoClinicoTest {

    private static Nodo n(String id) {
        return n(id, "pregunta", 0);
    }

    private static Nodo n(String id, String tipo, int peso) {
        return new Nodo(id, tipo, "contenido-" + id, peso);
    }

    // ---------- estructura básica ----------

    @Test
    void agregarNodoMarcaInicial() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        g.agregarNodo(raiz);
        assertEquals(1, g.size());
        assertEquals(raiz, g.obtenerNodoInicial().orElseThrow());
    }

    // ---------- aristas ----------

    @Test
    void conectarRespuestasOk() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        g.agregarNodo(a);
        g.agregarNodo(b);
        g.conectarRespuestas(a, b, "Sí");
        List<Respuesta> opciones = g.obtenerOpciones(a);
        assertEquals(1, opciones.size());
        assertEquals("Sí", opciones.get(0).getCondicion());
        assertEquals(b, opciones.get(0).getDestino());
    }

    // ---------- eliminaciones ----------

    @Test
    void eliminarNodoBorraAristasEntrantes() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        Nodo c = n("c");
        for (Nodo x : new Nodo[] { a, b, c }) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(a, b, "Sí");
        g.conectarRespuestas(c, b, "Sí");

        g.eliminarNodo(b);
        assertFalse(g.contains(b));
        assertTrue(g.obtenerOpciones(a).isEmpty());
        assertTrue(g.obtenerOpciones(c).isEmpty());
    }

    // ---------- validaciones ----------

    @Test
    void rutaValidaOk() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        Nodo t = n("t", "recomendacion", 0);
        g.agregarNodo(raiz);
        g.agregarNodo(t);
        g.conectarRespuestas(raiz, t, "Sí");
        assertTrue(g.rutaValida());
    }

    @Test
    void rutaInvalidaPorHuerfano() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        Nodo huerfano = n("h", "recomendacion", 0);
        g.agregarNodo(raiz);
        g.agregarNodo(huerfano);
        assertFalse(g.rutaValida());
    }

    @Test
    void validarCicloAciclico() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b", "recomendacion", 0);
        g.agregarNodo(a);
        g.agregarNodo(b);
        g.conectarRespuestas(a, b, "Sí");
        assertTrue(g.validarCiclo());
    }

    @Test
    void validarCicloDetectaCiclo() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        g.agregarNodo(a);
        g.agregarNodo(b);
        g.conectarRespuestas(a, b, "Sí");
        g.conectarRespuestas(b, a, "Sí");
        assertFalse(g.validarCiclo());
    }

    // ---------- evaluarPaso y camino causal ----------

    @Test
    void evaluarPaso() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        Nodo c = n("c");
        for (Nodo x : new Nodo[] { a, b, c }) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(a, b, "Sí");
        g.conectarRespuestas(a, c, "No");
        assertEquals(b, g.evaluarPaso(a, "Sí"));
        assertEquals(c, g.evaluarPaso(a, "No"));
    }

    @Test
    void obtenerCaminoCausal() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        Nodo p1 = n("p1");
        Nodo t = n("t", "recomendacion", 0);
        for (Nodo x : new Nodo[] { raiz, p1, t }) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(raiz, p1, "Sí");
        g.conectarRespuestas(p1, t, "≥38°C");

        List<PasoCausal> camino = g.obtenerCaminoCausal(t);
        List<String> ids = new ArrayList<>();
        List<String> condiciones = new ArrayList<>();
        for (PasoCausal paso : camino) {
            ids.add(paso.nodo().getId());
            condiciones.add(paso.condicion());
        }
        assertEquals(List.of("raiz", "p1", "t"), ids);
        assertEquals(List.of("", "Sí", "≥38°C"), condiciones);
    }

    // ---------- navegar() ----------

    // ---------- tipo de interacción ----------

    @Test
    void tipoInteraccionSeInfiereDeLasOpciones() {
        GrafoClinico g = new GrafoClinico();
        Nodo siNo = n("si_no");
        Nodo unica = n("unica");
        Nodo t = n("t", "recomendacion", 1);
        g.agregarNodo(siNo);
        g.agregarNodo(unica);
        g.agregarNodo(t);
        g.conectarRespuestas(siNo, t, "SI");
        g.conectarRespuestas(siNo, unica, "NO");
        g.conectarRespuestas(unica, t, "A");
        g.conectarRespuestas(unica, t, "B");

        assertEquals("SI_NO", g.tipoInteraccion(siNo));
        assertEquals("SELECCION_UNICA", g.tipoInteraccion(unica));
    }
}
