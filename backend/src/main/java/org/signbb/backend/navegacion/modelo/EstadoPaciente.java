package org.signbb.backend.navegacion.modelo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TAD EstadoPaciente (Sección 4.2 del documento de diseño).
 *
 * <p>
 * Representa una sesión de triaje en curso: las respuestas del padre a las
 * preguntas del grafo y el puntaje acumulado. No contiene lógica de
 * inferencia — solo almacena y consulta.
 *
 * <p>
 * Se usa {@code LinkedHashMap} para conservar el orden de inserción de las
 * respuestas (mismo comportamiento determinista que el dict de Python).
 *
 * <p>
 * MODIFICADO (multi-respuesta): una pregunta de selección múltiple puede
 * registrar varias respuestas para el mismo nodo. {@link #registrarRespuesta}
 * mantiene el contrato histórico (sobrescribe con una sola), mientras que
 * {@link #registrarRespuestas} guarda todas las opciones marcadas; las reglas
 * las consultan con {@link #getRespuestas}. También se recuerdan las reglas ya
 * disparadas ({@link #marcarReglaDisparada}) para que el encadenamiento hacia
 * adelante no vuelva a activarlas cuando una regla redirige el recorrido.
 *
 * <p>
 * MODIFICADO (retroceder): cada respuesta del recorrido se anota en una pila
 * de {@link PasoRecorrido} con las reglas que marcó. {@link #desapilarPaso}
 * deshace el último paso entero (su respuesta y sus reglas) sin reevaluar
 * nada; quién apila y cuándo lo decide el motor de navegación.
 *
 * <p>
 * MODIFICADO (selección múltiple con cola): además de las respuestas, el
 * estado guarda la navegación pendiente: la cola de opciones marcadas que
 * todavía no se recorren ({@link #encolarOpcion}), la ruta real que se está
 * siguiendo y las recomendaciones ya alcanzadas. Cada paso apilado lleva una
 * {@link FotoNavegacion} tomada antes de ejecutarse, que {@link #desapilarPaso}
 * restaura. Quién decide qué se encola y cuándo se retoma es el motor.
 *
 * <p>
 * Migrado desde {@code src/estado_paciente.py}.
 */
public final class EstadoPaciente {

    private final String idSesion;
    private final Map<String, List<String>> respuestas = new LinkedHashMap<>();
    private final Set<String> reglasDisparadas = new LinkedHashSet<>();
    private final List<PasoRecorrido> pasos = new ArrayList<>();
    private final List<OpcionPendiente> cola = new ArrayList<>();
    private final List<PasoRuta> ruta = new ArrayList<>();
    private final Map<String, List<PasoRuta>> recomendaciones = new LinkedHashMap<>();
    private int siguienteOrden = 0;
    private int puntaje = 0;

    /** Crea la sesión de triaje vacía. */
    public EstadoPaciente(String idSesion) {
        if (idSesion == null || idSesion.isEmpty()) {
            throw new IllegalArgumentException("idSesion debe ser una cadena no vacía");
        }
        this.idSesion = idSesion;
    }

    /** Identificador de la sesión. */
    public String getIdSesion() {
        return idSesion;
    }

    /**
     * Registra una respuesta única para el nodo dado, sobrescribiendo lo que
     * hubiera (contrato histórico de respuesta única).
     */
    public void registrarRespuesta(String idNodo, String respuesta) {
        registrarRespuestas(idNodo, List.of(respuesta));
    }

    /**
     * Registra todas las opciones marcadas por el cuidador para el nodo dado
     * (selección múltiple). Reemplaza lo registrado antes para ese nodo.
     */
    public void registrarRespuestas(String idNodo, List<String> respuestasMarcadas) {
        if (idNodo == null || idNodo.isEmpty()) {
            throw new IllegalArgumentException("idNodo debe ser una cadena no vacía");
        }
        if (respuestasMarcadas == null || respuestasMarcadas.isEmpty()) {
            throw new IllegalArgumentException("debe venir al menos una respuesta");
        }
        List<String> copia = new ArrayList<>();
        for (String r : respuestasMarcadas) {
            if (r == null || r.isEmpty()) {
                throw new IllegalArgumentException("respuesta debe ser una cadena no vacía");
            }
            copia.add(r);
        }
        respuestas.put(idNodo, copia);
    }

    /**
     * Primera respuesta registrada para {@code idNodo}, si existe. Para
     * selección múltiple use {@link #getRespuestas}.
     */
    public Optional<String> getRespuesta(String idNodo) {
        List<String> valores = respuestas.get(idNodo);
        if (valores == null || valores.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(valores.get(0));
    }

    /** Copia de todas las respuestas registradas para {@code idNodo}. */
    public List<String> getRespuestas(String idNodo) {
        List<String> valores = respuestas.get(idNodo);
        return valores == null ? List.of() : new ArrayList<>(valores);
    }

    /** True si {@code respuesta} está entre las registradas para {@code idNodo}. */
    public boolean contieneRespuesta(String idNodo, String respuesta) {
        List<String> valores = respuestas.get(idNodo);
        return valores != null && valores.contains(respuesta);
    }

    /**
     * Apila un paso ya aplicado: solo lo anota, no toca respuestas ni reglas
     * (las registró quien lo apila). Lo usan el motor y la reconstrucción de
     * una sesión guardada.
     */
    public void apilarPaso(PasoRecorrido paso) {
        if (paso == null) {
            throw new IllegalArgumentException("paso no puede ser nulo");
        }
        pasos.add(paso);
    }

    /**
     * Deshace el último paso: restaura las respuestas previas de su pregunta
     * (o la borra si no tenía) y desmarca las reglas que ese paso marcó.
     * Devuelve el paso deshecho, o vacío si la pila está vacía.
     */
    public Optional<PasoRecorrido> desapilarPaso() {
        if (pasos.isEmpty()) {
            return Optional.empty();
        }
        PasoRecorrido paso = pasos.remove(pasos.size() - 1);
        if (paso.respuestasPrevias().isEmpty()) {
            respuestas.remove(paso.idPregunta());
        } else {
            respuestas.put(paso.idPregunta(), new ArrayList<>(paso.respuestasPrevias()));
        }
        paso.reglasMarcadas().forEach(reglasDisparadas::remove);
        if (paso.navegacionPrevia() != null) {
            restaurarNavegacion(paso.navegacionPrevia());
        }
        return Optional.of(paso);
    }

    /** Copia de la pila de pasos, del primero al último. */
    public List<PasoRecorrido> pasos() {
        return new ArrayList<>(pasos);
    }

    /**
     * Anota una opción marcada por el cuidador que el recorrido todavía no
     * sigue. Recibe el número de orden de llegada, que desempata a igual
     * gravedad.
     *
     * @return {@code true} si se agregó; {@code false} (sin consumir número de
     *         orden) si ya había una opción pendiente hacia el mismo destino.
     */
    public boolean encolarOpcion(String idOrigen, String condicion, String idDestino,
            List<PasoRuta> camino) {
        if (colaContieneDestino(idDestino)) {
            return false;
        }
        cola.add(new OpcionPendiente(idOrigen, condicion, idDestino, siguienteOrden, camino));
        siguienteOrden++;
        return true;
    }

    /** True si hay una opción pendiente que lleva a {@code idDestino}. */
    public boolean colaContieneDestino(String idDestino) {
        for (OpcionPendiente o : cola) {
            if (o.idDestino().equals(idDestino)) {
                return true;
            }
        }
        return false;
    }

    /** Copia de las opciones pendientes, en el orden en que llegaron. */
    public List<OpcionPendiente> cola() {
        return new ArrayList<>(cola);
    }

    /** Quita una opción pendiente (la que el motor decidió seguir). */
    public boolean quitarDeCola(OpcionPendiente opcion) {
        return cola.remove(opcion);
    }

    /** Descarta todas las opciones pendientes (el recorrido terminó con alerta). */
    public void vaciarCola() {
        cola.clear();
    }

    /** Copia de la ruta real que se está siguiendo. */
    public List<PasoRuta> ruta() {
        return new ArrayList<>(ruta);
    }

    /** Agrega un paso al final de la ruta que se está siguiendo. */
    public void agregarARuta(PasoRuta paso) {
        if (paso == null) {
            throw new IllegalArgumentException("paso no puede ser nulo");
        }
        ruta.add(paso);
    }

    /** Reemplaza la ruta (al retomar una opción pendiente, con su propio camino). */
    public void reemplazarRuta(List<PasoRuta> nueva) {
        List<PasoRuta> copia = nueva == null ? List.of() : new ArrayList<>(nueva);
        ruta.clear();
        ruta.addAll(copia);
    }

    /**
     * Anota una recomendación alcanzada, con el camino que llevó a ella. Se
     * conserva el orden de llegada y no se repite.
     *
     * @return {@code true} si se agregó; {@code false} si ya estaba.
     */
    public boolean registrarRecomendacion(String idNodo, List<PasoRuta> camino) {
        if (idNodo == null || idNodo.isEmpty()) {
            throw new IllegalArgumentException("idNodo debe ser una cadena no vacía");
        }
        if (recomendaciones.containsKey(idNodo)) {
            return false;
        }
        recomendaciones.put(idNodo, camino == null ? List.of() : List.copyOf(camino));
        return true;
    }

    /** Copia de las recomendaciones alcanzadas (id del nodo → camino), en orden de llegada. */
    public Map<String, List<PasoRuta>> recomendaciones() {
        Map<String, List<PasoRuta>> copia = new LinkedHashMap<>();
        for (Map.Entry<String, List<PasoRuta>> e : recomendaciones.entrySet()) {
            copia.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copia;
    }

    /** Foto inmutable de la navegación pendiente (cola, ruta y recomendaciones). */
    public FotoNavegacion fotografiarNavegacion() {
        return new FotoNavegacion(cola, ruta, recomendaciones, siguienteOrden);
    }

    /** Devuelve la navegación pendiente al estado de {@code foto}. */
    public void restaurarNavegacion(FotoNavegacion foto) {
        if (foto == null) {
            throw new IllegalArgumentException("foto no puede ser nula");
        }
        cola.clear();
        cola.addAll(foto.cola());
        ruta.clear();
        ruta.addAll(foto.ruta());
        recomendaciones.clear();
        recomendaciones.putAll(foto.recomendaciones());
        siguienteOrden = foto.siguienteOrden();
    }

    /** Marca una regla como ya disparada (no debe reevaluarse). */
    public void marcarReglaDisparada(String idRegla) {
        reglasDisparadas.add(idRegla);
    }

    /** True si la regla ya se disparó en este recorrido. */
    public boolean reglaDisparada(String idRegla) {
        return reglasDisparadas.contains(idRegla);
    }

    /** Copia de las reglas ya disparadas, en orden de inserción. */
    public Set<String> reglasDisparadas() {
        return new LinkedHashSet<>(reglasDisparadas);
    }

    /** Suma puntos al puntaje de la sesión. */
    public void sumarPuntaje(int delta) {
        puntaje += delta;
    }

    /** Puntaje acumulado de la sesión. */
    public int getPuntaje() {
        return puntaje;
    }

    /**
     * Copia de los síntomas registrados (no expone la estructura interna). Un
     * nodo de selección múltiple aporta un síntoma por cada respuesta marcada.
     */
    public List<Sintoma> sintomas() {
        List<Sintoma> lista = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : respuestas.entrySet()) {
            for (String respuesta : e.getValue()) {
                lista.add(new Sintoma(e.getKey(), respuesta));
            }
        }
        return lista;
    }

    /** Número de nodos con al menos una respuesta registrada. */
    public int size() {
        return respuestas.size();
    }

    @Override
    public String toString() {
        return "EstadoPaciente(idSesion='" + idSesion + "', respuestas="
                + respuestas.size() + ", puntaje=" + puntaje + ")";
    }
}
