package org.signbb.backend;

import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.PasoCausal;
import org.signbb.backend.navegacion.modelo.Respuesta;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.signbb.backend.reglas.modelo.ResultadoInferencia;
import org.signbb.backend.reglas.servicios.ServicioReglas;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Demo del TAD Grafo Clínico y del motor de reglas (migrada de
 * {@code ejemplo_triaje.py}).
 *
 * <p>
 * Muestra por consola:
 * <ul>
 * <li>La navegación automática por prioridad ({@code navegar()}).</li>
 * <li>El camino causal reconstruido hasta el diagnóstico.</li>
 * <li>Un recorrido paso a paso simulando respuestas del padre
 * ({@code evaluarPaso()}).</li>
 * <li>El grafo clínico y la base de reglas
 * ({@link ContenidoEjemplo}) ya compuestos: grafo raíz más los subgrafos de
 * respiración, gastrointestinal y actividad/color, con los ids prefijados.</li>
 * <li>La evaluación de reglas sobre cuatro escenarios de estado del
 * paciente (forward chaining + resolución por severidad).</li>
 * </ul>
 *
 * <p>
 * Se ejecuta automáticamente al arrancar la app Spring Boot.
 */
@Component
public class DemoTriajeRunner implements CommandLineRunner {

    /** Subgrafo cuyas preguntas usan los escenarios de la sección 6. */
    private static final String SUBGRAFO_RESPIRACION = "grafo_respiracion";

    private final ServicioReglas servicioReglas;

    /** Inyecta el servicio de reglas usado en la sección 6. */
    public DemoTriajeRunner(ServicioReglas servicioReglas) {
        this.servicioReglas = servicioReglas;
    }

    /** Puebla el mini grafo de fiebre de demo y retorna sus nodos por id. */
    private static Map<String, Nodo> construirGrafo(GrafoClinico g) {
        Map<String, Nodo> nodos = new LinkedHashMap<>();
        nodos.put("raiz", new Nodo("raiz", "pregunta", "¿El bebé tiene fiebre?", 1));
        nodos.put("temp", new Nodo("temp", "pregunta", "¿Cuál es la temperatura?", 2));
        nodos.put("letargo", new Nodo("letargo", "pregunta", "¿Está letárgico?", 2));
        nodos.put("urgencia", new Nodo("urgencia", "consulta_medica",
                "Consulta médica urgente inmediata", 3));
        nodos.put("consulta", new Nodo("consulta", "consulta_medica",
                "Agendar consulta médica pronto", 2));
        nodos.put("reco_hogar", new Nodo("reco_hogar", "recomendacion",
                "Cuidados en casa y observar", 1));
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

    /** Ejecuta las seis secciones de la demo al arrancar la app. */
    @Override
    public void run(String... args) {
        GrafoClinico g = new GrafoClinico();
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
        String[][] respuestasPadre = { { "Sí", "raiz" }, { "37-38°C", "temp" }, { "Sí", "letargo" } };
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

        separador("5. Grafo compuesto: raíz + respiración, gastro y activo/color");
        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        GrafoClinico desdeJson = GrafoClinicoMapper.desde(contenido.grafo());

        System.out.printf("Nodos cargados: %d%n", desdeJson.size());
        System.out.printf("Nodo inicial: %s%n",
                desdeJson.obtenerNodoInicial().map(Nodo::getId).orElse("(ninguno)"));
        System.out.printf("Ruta válida: %s%n", desdeJson.rutaValida());

        System.out.println("\nNodos:");
        for (Nodo n : desdeJson.obtenerNodos()) {
            System.out.printf("  %-44s (%-15s, peso %d) %s%n",
                    n.getId(), n.getTipo(), n.getPeso(), n.getContenido());
        }

        System.out.println("\nAristas:");
        for (Nodo n : desdeJson.obtenerNodos()) {
            for (Respuesta r : desdeJson.obtenerOpciones(n)) {
                System.out.printf("  %-44s --[%s]--> %s%n",
                        r.getOrigen().getId(), r.getCondicion(), r.getDestino().getId());
            }
        }

        separador("6. Base de reglas compuesta (ids prefijados por subgrafo)");
        BaseReglas base = BaseReglasMapper.desde(contenido.reglas(), desdeJson);
        System.out.printf("Reglas cargadas: %d%n", base.size());
        for (ReglaClinica r : base.todasLasReglas()) {
            System.out.printf("  %-24s [%-16s] %s → %s%n",
                    r.getIdRegla(), r.getNivel(), r.getNombre(),
                    r.getNodoDestino().getId());
        }

        String urgencia = CompositorGrafoClinico.prefijar(SUBGRAFO_RESPIRACION, "q_resp_urgencia");
        String consulta = CompositorGrafoClinico.prefijar(SUBGRAFO_RESPIRACION, "q_resp_consulta");
        String normal = CompositorGrafoClinico.prefijar(SUBGRAFO_RESPIRACION, "q_resp_normal");

        System.out.println("\nEscenario 1: quejido al respirar (R_RESP_URG URGENCIA)");
        EstadoPaciente grave = new EstadoPaciente("sesion-demo-1");
        grave.registrarRespuesta(urgencia, "Quejido al respirar");
        imprimirEvaluacion(base, grave);

        System.out.println("\nEscenario 2: sin signos de alarma, tos con respiración tranquila (R_RESP_CON CONSULTAR_MEDICO)");
        EstadoPaciente moderado = new EstadoPaciente("sesion-demo-2");
        moderado.registrarRespuesta(urgencia, "No, ninguno de los anteriores");
        moderado.registrarRespuesta(consulta, "Presenta tos pero respira tranquilo");
        imprimirEvaluacion(base, moderado);

        System.out.println("\nEscenario 3: solo condiciones comunes (nivel implícito NORMAL)");
        EstadoPaciente tranquilo = new EstadoPaciente("sesion-demo-3");
        tranquilo.registrarRespuesta(normal, "Estornudos o hipo");
        imprimirEvaluacion(base, tranquilo);
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
            System.out.printf("    %-24s [%-16s] %s%n",
                    r.getIdRegla(), r.getNivel(), r.getNombre());
        }
        ResultadoInferencia resultado = servicioReglas.evaluar(base, estado);
        System.out.printf("  Nivel resultante (NORMAL < CONSULTAR_MEDICO < URGENCIA): %s%n",
                resultado.nivel());
        System.out.println("  Razones combinadas del nivel máximo:");
        for (ReglaClinica r : resultado.reglas()) {
            System.out.printf("    %-24s → %s%n", r.getIdRegla(), r.getNodoDestino().getId());
        }
        System.out.printf("  Corto-circuito al destino de la primera regla: (%s) %s%n",
                resultado.destino().getId(), resultado.destino().getContenido());
    }
}
