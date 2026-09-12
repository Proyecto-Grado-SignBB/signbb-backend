package org.signbb.backend;

import org.signbb.backend.model.*;
import org.signbb.backend.reglas.servicios.ResultadoInferencia;
import org.signbb.backend.reglas.servicios.ServicioReglas;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Demo del TAD Grafo Clínico y del motor de reglas (migrada de
 * {@code ejemplo_triaje.py}).
 *
 * <p>Muestra por consola:
 * <ul>
 *   <li>La navegación automática por prioridad ({@code navegar()}).</li>
 *   <li>El camino causal reconstruido hasta el diagnóstico.</li>
 *   <li>Un recorrido paso a paso simulando respuestas del padre
 *       ({@code evaluarPaso()}).</li>
 *   <li>El grafo clínico y la base de reglas cargados desde
 *       {@code static/ejemplo_grafo.json} y {@code static/ejemplo_reglas.json}.</li>
 *   <li>La evaluación de reglas sobre cuatro escenarios de estado del
 *       paciente (forward chaining + resolución por severidad).</li>
 * </ul>
 *
 * <p>Se ejecuta automáticamente al arrancar la app Spring Boot.
 */
@Component
public class DemoTriajeRunner implements CommandLineRunner {

    private final ServicioReglas servicioReglas;

    /** Inyecta el servicio de reglas usado en la sección 6. */
    public DemoTriajeRunner(ServicioReglas servicioReglas) {
        this.servicioReglas = servicioReglas;
    }

    /** Puebla el mini grafo de fiebre de demo y retorna sus nodos por id. */
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

    /** Imprime un título entre líneas separadoras. */
    private static void separador(String titulo) {
        System.out.println("\n" + "=".repeat(60) + "\n" + titulo + "\n" + "=".repeat(60));
    }

    /** Lee un recurso del classpath como UTF-8. */
    private static String leerRecurso(String ruta) {
        try {
            return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
    }

    /** Ejecuta las seis secciones de la demo al arrancar la app. */
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
        GrafoClinico desdeJson = JsonParser.parseJsonToGrafoClinico(
                leerRecurso("static/ejemplo_grafo.json"));

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

        separador("6. Base de reglas cargada desde static/ejemplo_reglas.json");
        BaseReglas base = JsonParser.parseJsonToBaseReglas(
                leerRecurso("static/ejemplo_reglas.json"), desdeJson);
        System.out.printf("Reglas cargadas: %d%n", base.size());
        for (ReglaClinica r : base.todasLasReglas()) {
            System.out.printf("  %-3s [%-16s] %s → %s%n",
                    r.getIdRegla(), r.getNivel(), r.getNombre(),
                    r.getNodoDestino().getId());
        }

        System.out.println("\nEscenario 1: coloración + menos de 24h + come menos + letargo");
        EstadoPaciente preocupante = new EstadoPaciente("sesion-demo-1");
        preocupante.registrarRespuesta("pregunta_coloracion", "SI");
        preocupante.registrarRespuesta("pregunta_tiempo", "MENOS_DE_24_HORAS");
        preocupante.registrarRespuesta("pregunta_alimentacion", "SI");
        preocupante.registrarRespuesta("pregunta_letargo", "SI");
        imprimirEvaluacion(base, preocupante);

        System.out.println("\nEscenario 2: sin señales de alerta (todo NO)");
        EstadoPaciente tranquilo = new EstadoPaciente("sesion-demo-2");
        tranquilo.registrarRespuesta("pregunta_coloracion", "NO");
        tranquilo.registrarRespuesta("pregunta_poposangre", "NO");
        imprimirEvaluacion(base, tranquilo);

        System.out.println("\nEscenario 3: coloración + más de 24h + letargo (empate URGENCIA)");
        EstadoPaciente empate = new EstadoPaciente("sesion-demo-3");
        empate.registrarRespuesta("pregunta_coloracion", "SI");
        empate.registrarRespuesta("pregunta_tiempo", "MAS_DE_24_HORAS");
        empate.registrarRespuesta("pregunta_letargo", "SI");
        imprimirEvaluacion(base, empate);

        System.out.println("\nEscenario 4: coloración + come menos, sin letargo (solo CONSULTAR_MEDICO)");
        EstadoPaciente moderado = new EstadoPaciente("sesion-demo-4");
        moderado.registrarRespuesta("pregunta_coloracion", "SI");
        moderado.registrarRespuesta("pregunta_alimentacion", "SI");
        moderado.registrarRespuesta("pregunta_letargo", "NO");
        imprimirEvaluacion(base, moderado);
    }

    /** Imprime las reglas activadas, el nivel resultante y el corto-circuito. */
    private void imprimirEvaluacion(BaseReglas base, EstadoPaciente estado) {
        List<ReglaClinica> activadas = servicioReglas.obtenerReglasActivadas(base, estado);
        if (activadas.isEmpty()) {
            System.out.println("  Reglas activadas: ninguna (nivel implícito NORMAL)");
            return;
        }
        System.out.println("  Reglas activadas (forward chaining):");
        for (ReglaClinica r : activadas) {
            System.out.printf("    %-3s [%-16s] %s%n",
                    r.getIdRegla(), r.getNivel(), r.getNombre());
        }
        ResultadoInferencia resultado = servicioReglas.evaluar(base, estado);
        System.out.printf("  Nivel resultante (NORMAL < CONSULTAR_MEDICO < URGENCIA): %s%n",
                resultado.nivel());
        System.out.println("  Razones combinadas del nivel máximo:");
        for (ReglaClinica r : resultado.reglas()) {
            System.out.printf("    %-3s → %s%n", r.getIdRegla(), r.getNodoDestino().getId());
        }
        System.out.printf("  Corto-circuito al destino de la primera regla: (%s) %s%n",
                resultado.destino().getId(), resultado.destino().getContenido());
    }
}
