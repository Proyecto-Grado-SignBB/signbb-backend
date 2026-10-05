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
 * Migrado desde {@code src/estado_paciente.py}.
 */
public final class EstadoPaciente {

    private final String idSesion;
    private final Map<String, List<String>> respuestas = new LinkedHashMap<>();
    private final Set<String> reglasDisparadas = new LinkedHashSet<>();
    private final List<PasoRecorrido> pasos = new ArrayList<>();
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
     * Copia de las respuestas por nodo en el orden en que se respondieron las
     * preguntas. Es el historial del recorrido: el motor lo reproduce para
     * volver a la pregunta anterior.
     */
    public Map<String, List<String>> respuestasEnOrden() {
        Map<String, List<String>> copia = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : respuestas.entrySet()) {
            copia.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copia;
    }

    /**
     * Id de la última pregunta respondida, o vacío si aún no hay respuestas.
     * Si hay pila de pasos, es la pregunta de su cima; si no, la última
     * insertada en las respuestas.
     */
    public Optional<String> ultimaPreguntaRespondida() {
        if (!pasos.isEmpty()) {
            return Optional.of(pasos.get(pasos.size() - 1).idPregunta());
        }
        String ultima = null;
        for (String idNodo : respuestas.keySet()) {
            ultima = idNodo;
        }
        return Optional.ofNullable(ultima);
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
        return Optional.of(paso);
    }

    /** Copia de la pila de pasos, del primero al último. */
    public List<PasoRecorrido> pasos() {
        return new ArrayList<>(pasos);
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

    /** Vacía las respuestas, las reglas disparadas, la pila de pasos y reinicia el puntaje. */
    public void reiniciar() {
        respuestas.clear();
        reglasDisparadas.clear();
        pasos.clear();
        puntaje = 0;
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
