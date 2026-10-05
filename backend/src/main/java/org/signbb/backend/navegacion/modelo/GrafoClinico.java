package org.signbb.backend.navegacion.modelo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * TAD GrafoClinico (Secciones 4 y 5 del documento de diseño).
 *
 * <p>
 * Implementa las 13 operaciones especificadas más la operación *
 * {@link #navegar()} descrita en la sección 5.1.
 *
 * <p>
 * Representación interna: lista de adyacencia ({@code LinkedHashMap} de
 * {@link Nodo} → lista de {@link Respuesta}). Se prefiere sobre matriz porque
 * el
 * grafo es disperso y la operación dominante es "obtener aristas salientes de
 * un
 * nodo". Se usa LinkedHashMap para conservar el orden de inserción de los nodos
 * (igual que el {@code dict} de Python), lo que hace determinista tanto la
 * elección del nodo inicial tras un borrado como los recorridos.
 *
 * <p>
 * Migrado desde {@code src/grafo_clinico.py}.
 */
public final class GrafoClinico {

    private final Map<Nodo, List<Respuesta>> adyacencia = new LinkedHashMap<>();
    private Nodo nodoInicial = null;
    private final int umbralAlto;
    private final int umbralBajo;
    private boolean bloqueado = false;

    /**
     * Operación 1: crearGrafo() con umbrales por defecto para la escala de
     * peso 1–3 (alto=3: un terminal urgente corta; bajo=1: no se descarta
     * ninguno).
     */
    public GrafoClinico() {
        this(3, 1);
    }

    /** Crea el grafo con umbrales de peso explícitos. */
    public GrafoClinico(int umbralAlto, int umbralBajo) {
        if (umbralBajo > umbralAlto) {
            throw new IllegalArgumentException(
                    "umbralBajo no puede ser mayor que umbralAlto");
        }
        this.umbralAlto = umbralAlto;
        this.umbralBajo = umbralBajo;
    }

    /** Operación 2. El primer nodo agregado queda como inicial. */
    public void agregarNodo(Nodo nuevo) {
        if (nuevo == null) {
            throw new IllegalArgumentException("nuevo debe ser Nodo");
        }
        if (adyacencia.containsKey(nuevo)) {
            throw new IllegalArgumentException(
                    "El nodo " + nuevo.getId() + " ya existe en el grafo");
        }
        adyacencia.put(nuevo, new ArrayList<>());
        if (nodoInicial == null) {
            nodoInicial = nuevo;
        }
    }

    /** Operación 3. */
    public void conectarRespuestas(Nodo origen, Nodo destino, String condicion) {
        if (!adyacencia.containsKey(origen)) {
            throw new IllegalArgumentException(
                    "origen " + origen.getId() + " no está en el grafo");
        }
        if (!adyacencia.containsKey(destino)) {
            throw new IllegalArgumentException(
                    "destino " + destino.getId() + " no está en el grafo");
        }
        if (origen.esTerminal()) {
            throw new IllegalArgumentException(
                    "no se puede conectar desde un nodo terminal: " + origen.getId());
        }
        for (Respuesta r : adyacencia.get(origen)) {
            if (r.getCondicion().equals(condicion)) {
                throw new IllegalArgumentException(
                        "ya existe una respuesta con condición '" + condicion
                                + "' desde " + origen.getId());
            }
        }
        adyacencia.get(origen).add(new Respuesta(origen, destino, condicion));
    }

    /** Operación 4. Elimina el nodo y las aristas entrantes hacia él. */
    public void eliminarNodo(Nodo nodo) {
        if (!adyacencia.containsKey(nodo)) {
            throw new IllegalArgumentException(
                    "el nodo " + nodo.getId() + " no está en el grafo");
        }
        adyacencia.remove(nodo);
        for (Map.Entry<Nodo, List<Respuesta>> e : adyacencia.entrySet()) {
            List<Respuesta> filtradas = new ArrayList<>();
            for (Respuesta r : e.getValue()) {
                if (!r.getDestino().equals(nodo)) {
                    filtradas.add(r);
                }
            }
            e.setValue(filtradas);
        }
        if (nodo.equals(nodoInicial)) {
            nodoInicial = adyacencia.isEmpty()
                    ? null
                    : adyacencia.keySet().iterator().next();
        }
    }

    /** Operación 5. */
    public void eliminarRespuesta(Nodo origen, String condicion) {
        if (!adyacencia.containsKey(origen)) {
            throw new IllegalArgumentException(
                    "el nodo " + origen.getId() + " no está en el grafo");
        }
        List<Respuesta> aristas = adyacencia.get(origen);
        List<Respuesta> nuevas = new ArrayList<>();
        for (Respuesta r : aristas) {
            if (!r.getCondicion().equals(condicion)) {
                nuevas.add(r);
            }
        }
        if (nuevas.size() == aristas.size()) {
            throw new IllegalArgumentException(
                    "no existe respuesta con condición '" + condicion + "' desde "
                            + origen.getId());
        }
        adyacencia.put(origen, nuevas);
    }

    /** Operación 6. */
    public Optional<Nodo> obtenerNodoInicial() {
        return Optional.ofNullable(nodoInicial);
    }

    /** Fija el nodo inicial de la navegación (debe pertenecer al grafo). */
    public void setNodoInicial(Nodo nodo) {
        if (!adyacencia.containsKey(nodo)) {
            throw new IllegalArgumentException(
                    "el nodo " + nodo.getId() + " no está en el grafo");
        }
        nodoInicial = nodo;
    }

    /** Operación 7. */
    public boolean esNodoTerminal(Nodo nodo) {
        return nodo.esTerminal();
    }

    /** Operación 8 — retorna copia para no exponer la estructura interna. */
    public List<Respuesta> obtenerOpciones(Nodo nodoActual) {
        if (!adyacencia.containsKey(nodoActual)) {
            throw new IllegalArgumentException(
                    "el nodo " + nodoActual.getId() + " no está en el grafo");
        }
        return new ArrayList<>(adyacencia.get(nodoActual));
    }

    /**
     * Operación adicional: tipo de interacción efectivo de una pregunta. Usa el
     * {@code tipoInteraccion} declarado en el nodo si viene; si no, lo infiere
     * de sus opciones: {@code SI_NO} cuando son exactamente {SI, NO} y
     * {@code SELECCION_UNICA} en cualquier otro caso. {@code SELECCION_MULTIPLE}
     * solo se obtiene declarándolo en el nodo. Lo comparten el motor (que solo
     * admite varias respuestas en {@code SELECCION_MULTIPLE}) y la vista REST.
     */
    public String tipoInteraccion(Nodo pregunta) {
        if (pregunta.getTipoInteraccion() != null) {
            return pregunta.getTipoInteraccion();
        }
        List<Respuesta> opciones = obtenerOpciones(pregunta);
        boolean esSiNo = opciones.size() == 2
                && opciones.stream().anyMatch(r -> "SI".equals(r.getCondicion()))
                && opciones.stream().anyMatch(r -> "NO".equals(r.getCondicion()));
        return esSiNo ? "SI_NO" : "SELECCION_UNICA";
    }

    /** Operación 9. */
    public boolean rutaValida() {
        if (adyacencia.isEmpty()) {
            return true;
        }
        Set<Nodo> alcanzados = new HashSet<>();
        for (List<Respuesta> aristas : adyacencia.values()) {
            for (Respuesta r : aristas) {
                alcanzados.add(r.getDestino());
            }
        }
        for (Map.Entry<Nodo, List<Respuesta>> e : adyacencia.entrySet()) {
            Nodo nodo = e.getKey();
            if (!nodo.equals(nodoInicial) && !alcanzados.contains(nodo)) {
                return false;
            }
            if (!nodo.esTerminal() && e.getValue().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Operación 10 — detiene navegar() en el siguiente paso. */
    public void bloquearRuta() {
        bloqueado = true;
    }

    /** Reversa de {@link #bloquearRuta()}: reanuda la navegación. */
    public void desbloquearRuta() {
        bloqueado = false;
    }

    /** Operación 11 — retorna True si el grafo es acíclico (DFS 3 colores). */
    public boolean validarCiclo() {
        Map<Nodo, Integer> color = new HashMap<>();
        for (Nodo n : adyacencia.keySet()) {
            color.put(n, BLANCO);
        }
        for (Nodo n : adyacencia.keySet()) {
            if (color.get(n) == BLANCO) {
                if (!dfsAciclico(n, color)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final int BLANCO = 0;
    private static final int GRIS = 1;
    private static final int NEGRO = 2;

    private boolean dfsAciclico(Nodo u, Map<Nodo, Integer> color) {
        color.put(u, GRIS);
        for (Respuesta r : adyacencia.get(u)) {
            Nodo v = r.getDestino();
            if (color.get(v) == GRIS) {
                return false;
            }
            if (color.get(v) == BLANCO && !dfsAciclico(v, color)) {
                return false;
            }
        }
        color.put(u, NEGRO);
        return true;
    }

    /** Operación 12 — BFS desde el inicial guardando el padre de cada nodo. */
    public List<PasoCausal> obtenerCaminoCausal(Nodo nodoResultado) {
        if (nodoInicial == null) {
            return new ArrayList<>();
        }
        if (!adyacencia.containsKey(nodoResultado)) {
            return new ArrayList<>();
        }
        if (nodoResultado.equals(nodoInicial)) {
            List<PasoCausal> unico = new ArrayList<>();
            unico.add(new PasoCausal(nodoInicial, ""));
            return unico;
        }

        Map<Nodo, PasoCausal> padre = new HashMap<>();
        Set<Nodo> visitados = new HashSet<>();
        visitados.add(nodoInicial);
        Deque<Nodo> cola = new ArrayDeque<>();
        cola.add(nodoInicial);
        boolean encontrado = false;
        while (!cola.isEmpty()) {
            Nodo actual = cola.pollFirst();
            if (actual.equals(nodoResultado)) {
                encontrado = true;
                break;
            }
            for (Respuesta r : adyacencia.get(actual)) {
                Nodo v = r.getDestino();
                if (!visitados.contains(v)) {
                    visitados.add(v);
                    padre.put(v, new PasoCausal(actual, r.getCondicion()));
                    cola.add(v);
                }
            }
        }

        if (!encontrado && !padre.containsKey(nodoResultado)) {
            return new ArrayList<>();
        }

        List<PasoCausal> camino = new ArrayList<>();
        Nodo actual = nodoResultado;
        while (!actual.equals(nodoInicial)) {
            PasoCausal p = padre.get(actual);
            camino.add(new PasoCausal(actual, p.condicion()));
            actual = p.nodo();
        }
        camino.add(new PasoCausal(nodoInicial, ""));
        java.util.Collections.reverse(camino);
        return camino;
    }

    /** Operación 13. */
    public Nodo evaluarPaso(Nodo nodoActual, String respuestaPadre) {
        if (!adyacencia.containsKey(nodoActual)) {
            throw new IllegalArgumentException(
                    "el nodo " + nodoActual.getId() + " no está en el grafo");
        }
        for (Respuesta r : adyacencia.get(nodoActual)) {
            if (r.getCondicion().equals(respuestaPadre)) {
                return r.getDestino();
            }
        }
        throw new IllegalArgumentException(
                "no hay respuesta con condición '" + respuestaPadre + "' desde "
                        + nodoActual.getId());
    }

    /**
     * Entrada de la cola de prioridad de {@link #navegar()}. Reemplaza la tupla
     * {@code (-peso, contador, nodo)} usada con heapq en Python.
     */
    private record Entrada(int peso, int contador, Nodo nodo) {
    }

    /**
     * Operación adicional (sección 5.1). BFS con cola de prioridad (max-heap por
     * peso) para explorar primero las preguntas de mayor urgencia. El contador
     * desempata de forma determinista respetando el orden de inserción.
     */
    public Optional<Nodo> navegar() {
        if (nodoInicial == null) {
            return Optional.empty();
        }

        // Mayor peso primero; a igual peso, menor contador primero.
        PriorityQueue<Entrada> cola = new PriorityQueue<>(
                Comparator.comparingInt((Entrada e) -> -e.peso())
                        .thenComparingInt(Entrada::contador));
        int contador = 0;
        cola.add(new Entrada(nodoInicial.getPeso(), contador, nodoInicial));
        Set<Nodo> visitados = new HashSet<>();
        Nodo mejorTerminal = null;

        while (!cola.isEmpty() && !bloqueado) {
            Nodo actual = cola.poll().nodo();
            if (visitados.contains(actual)) {
                continue;
            }
            visitados.add(actual);

            for (Respuesta r : adyacencia.getOrDefault(actual, List.of())) {
                Nodo destino = r.getDestino();
                if (destino.esTerminal()) {
                    if (destino.getPeso() >= umbralAlto) {
                        return Optional.of(destino);
                    }
                    if (destino.getPeso() < umbralBajo) {
                        continue;
                    }
                    if (mejorTerminal == null
                            || destino.getPeso() > mejorTerminal.getPeso()) {
                        mejorTerminal = destino;
                    }
                } else {
                    if (!visitados.contains(destino)) {
                        contador++;
                        cola.add(new Entrada(destino.getPeso(), contador, destino));
                    }
                }
            }
        }

        return Optional.ofNullable(mejorTerminal);
    }

    /** Número de nodos en el grafo. */
    public int size() {
        return adyacencia.size();
    }

    /** True si el nodo pertenece al grafo. */
    public boolean contains(Nodo nodo) {
        return adyacencia.containsKey(nodo);
    }

    /** Busca un nodo por su id. */
    public Optional<Nodo> obtenerNodoPorId(String id) {
        for (Nodo n : adyacencia.keySet()) {
            if (n.getId().equals(id)) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    /** Nodos del grafo en orden de inserción — retorna copia de las llaves. */
    public List<Nodo> obtenerNodos() {
        return new ArrayList<>(adyacencia.keySet());
    }
}
