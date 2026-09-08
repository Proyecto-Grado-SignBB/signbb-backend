package org.signbb.backend;

import org.signbb.backend.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Demo del TAD Grafo Clínico (migrada de {@code ejemplo_triaje.py}).
 *
 * <p>Construye un mini grafo de triaje neonatal y muestra por consola:
 * <ul>
 *   <li>La navegación automática por prioridad ({@code navegar()}).</li>
 *   <li>El camino causal reconstruido hasta el diagnóstico.</li>
 *   <li>Un recorrido paso a paso simulando respuestas del padre
 *       ({@code evaluarPaso()}).</li>
 * </ul>
 *
 * <p>Se ejecuta automáticamente al arrancar la app Spring Boot.
 */
@Component
public class DemoTriajeRunner implements CommandLineRunner {

    private static Map<String, Nodo> construirGrafo(GrafoClinico g) {
        Map<String, Nodo> nodos = new LinkedHashMap<>();
        nodos.put("raiz", new Nodo("raiz", "pregunta", "¿El bebé tiene fiebre?", 7));
        nodos.put("temp", new Nodo("temp", "pregunta", "¿Cuál es la temperatura?", 9));
        nodos.put("letargo", new Nodo("letargo", "pregunta", "¿Está letárgico?", 6));
        nodos.put("urgencia", new Nodo("urgencia", "consulta_medica",
                "Consulta médica urgente inmediata", 10));
        nodos.put("consulta", new Nodo("consulta", "consulta_medica",
                "Agendar consulta médica pronto", 7));
        nodos.put("reco_hogar", new Nodo("reco_hogar", "recomendacion",
                "Cuidados en casa y observar", 4));
        nodos.put("descarte", new Nodo("descarte", "recomendacion",
                "Sin señales de alerta", 1));

        for (Nodo n : nodos.values()) {
            g.agregarNodo(n);
        }

        g.conectarRespuestas(nodos.get("raiz"), nodos.get("temp"), "Sí");
        g.conectarRespuestas(nodos.get("raiz"), nodos.get("descarte"), "No");

        g.conectarRespuestas(nodos.get("temp"), nodos.get("urgencia"), "≥38°C");
        g.conectarRespuestas(nodos.get("temp"), nodos.get("letargo"), "37-38°C");

        g.conectarRespuestas(nodos.get("letargo"), nodos.get("consulta"), "Sí");
        g.conectarRespuestas(nodos.get("letargo"), nodos.get("reco_hogar"), "No");

        return nodos;
    }

    private static void separador(String titulo) {
        System.out.println("\n" + "=".repeat(60) + "\n" + titulo + "\n" + "=".repeat(60));
    }

    @Override
    public void run(String... args) {
        GrafoClinico g = new GrafoClinico(8, 3);
        construirGrafo(g);

        separador("1. Validaciones del grafo");
        System.out.printf("Nodos totales:        %d%n", g.size());
        System.out.printf("Nodo inicial:         %s%n",
                g.obtenerNodoInicial().map(Nodo::getId).orElse("(ninguno)"));
        System.out.printf("Ruta válida:          %s%n", g.rutaValida());
        System.out.printf("Acíclico:             %s%n", g.validarCiclo());

        separador("2. Navegación automática (navegar)");
        Nodo resultado = g.navegar().orElseThrow();
        System.out.printf("Diagnóstico priorizado: '%s' — '%s' (peso %d)%n",
                resultado.getId(), resultado.getContenido(), resultado.getPeso());

        separador("3. Camino causal hasta el diagnóstico");
        for (PasoCausal paso : g.obtenerCaminoCausal(resultado)) {
            Nodo n = paso.nodo();
            String flecha = paso.condicion().isEmpty()
                    ? ""
                    : " --[" + paso.condicion() + "]-->";
            System.out.printf("  %-12s (%s)%s%n", n.getId(), n.getTipo(), flecha);
        }

        separador("4. Recorrido paso a paso (respuestas simuladas del padre)");
        String[][] respuestasPadre = {{"Sí", "raiz"}, {"37-38°C", "temp"}, {"Sí", "letargo"}};
        Nodo actual = g.obtenerNodoInicial().orElseThrow();
        System.out.printf("Inicio: %s — %s%n", actual.getId(), actual.getContenido());
        for (String[] par : respuestasPadre) {
            String cond = par[0];
            String esperado = par[1];
            assert actual.getId().equals(esperado);
            actual = g.evaluarPaso(actual, cond);
            System.out.printf("  respuesta '%s' → %s (%s)%n",
                    cond, actual.getId(), actual.getTipo());
            if (g.esNodoTerminal(actual)) {
                System.out.printf("%n  Terminal alcanzado: %s%n", actual.getContenido());
                break;
            }
        }

        separador("5. Grafo cargado desde static/ejemplo_grafo.json");
        String json;
        try {
            json = new ClassPathResource("static/ejemplo_grafo.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer ejemplo_grafo.json", e);
        }
        GrafoClinico desdeJson = JsonParser.parseJsonToGrafoClinico(json);

        System.out.printf("Nodos cargados: %d%n", desdeJson.size());
        System.out.printf("Nodo inicial: %s%n",
                desdeJson.obtenerNodoInicial().map(Nodo::getId).orElse("(ninguno)"));
        System.out.printf("Ruta válida: %s%n", desdeJson.rutaValida());

        System.out.println("\nNodos:");
        for (Nodo n : desdeJson.obtenerNodos()) {
            System.out.printf("  %-28s (%-15s, peso %3d) %s%n",
                    n.getId(), n.getTipo(), n.getPeso(), n.getContenido());
        }

        System.out.println("\nAristas:");
        for (Nodo n : desdeJson.obtenerNodos()) {
            for (Respuesta r : desdeJson.obtenerOpciones(n)) {
                System.out.printf("  %-28s --[%s]--> %s%n",
                        r.getOrigen().getId(), r.getCondicion(), r.getDestino().getId());
            }
        }
    }
}
