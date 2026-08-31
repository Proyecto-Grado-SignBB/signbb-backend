package org.signbb.backend.model;

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
    void grafoIniciaVacio() {
        GrafoClinico g = new GrafoClinico();
        assertEquals(0, g.size());
        assertTrue(g.obtenerNodoInicial().isEmpty());
        assertTrue(g.rutaValida());
    }

    @Test
    void agregarNodoMarcaInicial() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        g.agregarNodo(raiz);
        assertEquals(1, g.size());
        assertEquals(raiz, g.obtenerNodoInicial().orElseThrow());
    }

    @Test
    void agregarNodoDuplicadoFalla() {
        GrafoClinico g = new GrafoClinico();
        Nodo x = n("x");
        g.agregarNodo(x);
        assertThrows(IllegalArgumentException.class, () -> g.agregarNodo(x));
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

    @Test
    void conectarDesdeNodoTerminalFalla() {
        GrafoClinico g = new GrafoClinico();
        Nodo t = n("t", "recomendacion", 0);
        Nodo b = n("b");
        g.agregarNodo(t);
        g.agregarNodo(b);
        assertThrows(IllegalArgumentException.class, () -> g.conectarRespuestas(t, b, "Sí"));
    }

    @Test
    void conectarDuplicadoFalla() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        Nodo c = n("c");
        for (Nodo x : new Nodo[] {a, b, c}) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(a, b, "Sí");
        assertThrows(IllegalArgumentException.class, () -> g.conectarRespuestas(a, c, "Sí"));
    }

    @Test
    void conectarNodoDesconocidoFalla() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        g.agregarNodo(a);
        assertThrows(IllegalArgumentException.class, () -> g.conectarRespuestas(a, b, "Sí"));
    }

    // ---------- eliminaciones ----------

    @Test
    void eliminarNodoBorraAristasEntrantes() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        Nodo c = n("c");
        for (Nodo x : new Nodo[] {a, b, c}) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(a, b, "Sí");
        g.conectarRespuestas(c, b, "Sí");

        g.eliminarNodo(b);
        assertFalse(g.contains(b));
        assertTrue(g.obtenerOpciones(a).isEmpty());
        assertTrue(g.obtenerOpciones(c).isEmpty());
    }

    @Test
    void eliminarRespuesta() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        Nodo b = n("b");
        g.agregarNodo(a);
        g.agregarNodo(b);
        g.conectarRespuestas(a, b, "Sí");
        g.eliminarRespuesta(a, "Sí");
        assertTrue(g.obtenerOpciones(a).isEmpty());
    }

    @Test
    void eliminarRespuestaInexistenteFalla() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        g.agregarNodo(a);
        assertThrows(IllegalArgumentException.class, () -> g.eliminarRespuesta(a, "Sí"));
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
    void rutaInvalidaPorPreguntaSinSalidas() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        g.agregarNodo(raiz);
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
        for (Nodo x : new Nodo[] {a, b, c}) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(a, b, "Sí");
        g.conectarRespuestas(a, c, "No");
        assertEquals(b, g.evaluarPaso(a, "Sí"));
        assertEquals(c, g.evaluarPaso(a, "No"));
    }

    @Test
    void evaluarPasoInexistente() {
        GrafoClinico g = new GrafoClinico();
        Nodo a = n("a");
        g.agregarNodo(a);
        assertThrows(IllegalArgumentException.class, () -> g.evaluarPaso(a, "Sí"));
    }

    @Test
    void obtenerCaminoCausal() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        Nodo p1 = n("p1");
        Nodo t = n("t", "recomendacion", 0);
        for (Nodo x : new Nodo[] {raiz, p1, t}) {
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

    @Test
    void caminoCausalInalcanzable() {
        GrafoClinico g = new GrafoClinico();
        Nodo raiz = n("raiz");
        Nodo suelto = n("suelto", "recomendacion", 0);
        g.agregarNodo(raiz);
        g.agregarNodo(suelto);
        assertTrue(g.obtenerCaminoCausal(suelto).isEmpty());
    }

    // ---------- navegar() ----------

    @Test
    void navegarUmbralAltoParaInmediatamente() {
        GrafoClinico g = new GrafoClinico(8, 3);
        Nodo raiz = n("raiz", "pregunta", 5);
        Nodo urgente = n("urgente", "consulta_medica", 10);
        Nodo leve = n("leve", "recomendacion", 5);
        for (Nodo x : new Nodo[] {raiz, urgente, leve}) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(raiz, urgente, "fiebre alta");
        g.conectarRespuestas(raiz, leve, "sin fiebre");

        assertEquals(urgente, g.navegar().orElseThrow());
    }

    @Test
    void navegarIgnoraDescartesBajos() {
        GrafoClinico g = new GrafoClinico(8, 3);
        Nodo raiz = n("raiz", "pregunta", 5);
        Nodo intermedio = n("intermedio", "pregunta", 4);
        Nodo descarte = n("descarte", "recomendacion", 1);
        Nodo normal = n("normal", "recomendacion", 5);
        for (Nodo x : new Nodo[] {raiz, intermedio, descarte, normal}) {
            g.agregarNodo(x);
        }
        g.conectarRespuestas(raiz, descarte, "poco");
        g.conectarRespuestas(raiz, intermedio, "seguir");
        g.conectarRespuestas(intermedio, normal, "algo");

        // descarte peso 1 < umbralBajo=3 → se ignora; normal peso 5 → gana
        assertEquals(normal, g.navegar().orElseThrow());
    }

    @Test
    void navegarSinNodoInicial() {
        GrafoClinico g = new GrafoClinico();
        assertTrue(g.navegar().isEmpty());
    }

    @Test
    void bloquearRutaDetieneNavegacion() {
        GrafoClinico g = new GrafoClinico(8, 3);
        Nodo raiz = n("raiz", "pregunta", 5);
        Nodo t = n("t", "recomendacion", 5);
        g.agregarNodo(raiz);
        g.agregarNodo(t);
        g.conectarRespuestas(raiz, t, "Sí");

        g.bloquearRuta();
        assertTrue(g.navegar().isEmpty());
        g.desbloquearRuta();
        assertEquals(t, g.navegar().orElseThrow());
    }
}
