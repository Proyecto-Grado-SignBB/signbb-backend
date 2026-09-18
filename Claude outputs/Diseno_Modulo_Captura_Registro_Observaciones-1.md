# Diseño del Módulo de Captura y Registro de Observaciones

**Proyecto:** SignBB · **Módulo:** Captura y Registro de Observaciones (paquete `org.signbb.backend.crobservaciones`)
**Estado del backend al momento de este diseño:** ya existen versiones funcionales del Motor de Reglas (`reglas.servicios.ServicioReglas`/`Impl`) y del Motor de Navegación (`navegacion.servicios.ServicioNavegacion`/`Impl`), cableados sobre el grafo clínico y la base de reglas en memoria (`navegacion.motor.ContextoClinico`).

Este documento sigue el mismo estilo de especificación (tipo TAD: operaciones, precondiciones, postcondiciones) usado en el javadoc de `Nodo`, `ReglaClinica`, `Contexto` y `EstadoPaciente`, para que pueda integrarse como una sección más del documento de diseño general del backend.

## 0. Alcance de esta versión

La arquitectura general del sistema contempla que el cuidador pueda aportar tres tipos de observación: texto, imagen propia y datos de sensores. **Esta versión del diseño cubre únicamente el canal de texto** — respuestas de tipo Sí/No, selección única y selección múltiple. Sensores e imágenes tomadas por el cuidador quedan reconocidos en la arquitectura (sección 8) pero **no se diseñan ni se implementan todavía**.

Sobre imágenes: las únicas que este módulo maneja hoy son las **imágenes de referencia que ya vienen con una pregunta** (p. ej. fotos de comparación para ictericia o cianosis) — y esas ya están resueltas por el Módulo de Navegación existente (`Nodo.tieneArchivos()` / `Nodo.getSrc()`, expuestas en `PreguntaDTO.tieneImagenes()` / `PreguntaDTO.imagenes()`). No hay nada nuevo que construir ahí; se documentan aquí solo para dejar explícito que ese caso ya está cubierto y no se confunda con "captura de imagen del cuidador".

---

## 1. Qué ya existe y qué falta

Del código compartido se reconstruye el flujo actual, puramente textual:

1. `ServicioNavegacion.iniciar()` abre una `SesionTriaje`, arranca el `MotorNavegacion` sobre el grafo (`ContextoClinico.getGrafo()`) y devuelve un `RecorridoDTO` con la primera `PreguntaDTO`.
2. El cuidador responde con un `String` (una `condicion` de arista, p. ej. `"SI"`, `"NO"`, `"MAS_DE_24_HORAS"`), recibido en un `RespuestaRequest` y pasado tal cual a `ServicioNavegacion.responder(recorridoId, respuesta)`.
3. `MotorNavegacion.paso(...)` avanza la arista correspondiente; en algún punto del recorrido se evalúan las reglas (`ServicioReglas.evaluar`) contra el `EstadoPaciente` acumulado.

Esto **ya cubre perfectamente** los casos Sí/No y selección única: en ambos, el cuidador elige exactamente una opción, y esa opción ya es literalmente la `condicion` que la arista espera. No hace falta ningún componente nuevo para esos dos casos — el "capturar y registrar" que les falta es, en todo caso, el registro de auditoría (sección 5), no la lógica de avance.

**Selección múltiple es el único caso que hoy no tiene dónde resolverse.** Si el cuidador puede marcar varias opciones de una misma pregunta a la vez (p. ej., dos síntomas respiratorios simultáneos), el grafo solo sabe seguir **una** arista por `condicion`. Alguien tiene que decidir, antes de llamar a `responder(...)`, cuál de las opciones marcadas "gana" — y esa es exactamente la regla que ya definimos en la Base de Signos y Reglas: **gana la opción de mayor peso/gravedad**. Ese "alguien" es este módulo.

---

## 2. Corrección de alcance: el desempate de selección múltiple es de Navegación

**Aclaración del equipo:** la resolución de una selección múltiple (decidir qué pasa cuando el cuidador marca varias opciones a la vez) se hace en el **Módulo de Navegación**, no aquí. Captura no decide nada sobre cuál opción "gana" — solo reúne todo lo que el cuidador marcó y se lo entrega completo a `ServicioNavegacion`. Esto corrige la versión anterior de este documento, que proponía un `ResolutorRespuesta` dentro de Captura; ese componente se elimina del diseño.

Con `JsonParser.java` y un JSON de grafo real ya a la vista, esto además aclara el panorama de datos:

- **El peso ya existe — pero es por NODO, no por opción de respuesta.** `metadata.peso` se lee para cada nodo (`meta.path("peso").asInt()`) y queda en `Nodo.getPeso()`. En el ejemplo, `consulta_medica_urgente` pesa 100, `recomendacion_tranquilidad` pesa 30, etc. — es la urgencia del **destino**, no de la opción que lo activa. Esto calza con que la resolución viva en Navegación: si dos opciones marcadas a la vez llevan a dos nodos destino distintos, Navegación puede comparar el `peso` de esos nodos candidatos y avanzar hacia el más urgente, sin que Captura necesite saber nada de pesos. **No hace falta ningún cambio de modelo para esto — ya está.**
- **Lo que sigue sin existir en ningún lado es el tipo de interacción** (Sí/No vs. selección única vs. selección múltiple). Ni `Nodo`, ni la metadata del JSON (`tipo`, `peso`, `archivos`, `src`, `categorias` — nada más), ni `PreguntaDTO` lo distinguen. Captura sí necesita saber esto, porque define cómo debe recolectar la entrada del cuidador (una opción, o varias) antes de pasarla a Navegación. Sigue siendo la única pieza de modelo que falta, y sigue siendo una decisión de Navegación/grafo, no algo que Captura pueda inventar por su cuenta: propongo la misma extensión aditiva de antes (un campo opcional `tipoInteraccion` en la metadata del nodo, con default `SELECCION_UNICA`, reflejado en `Nodo` y en `PreguntaDTO`), pero ahora **sin** el mapa de pesos por opción, que ya no hace falta.

**Dependencia real hacia Navegación — ahora confirmada con el código, no solo supuesta:** con `MotorNavegacion.java`, `ResultadoPaso.java` y `SesionTriaje.java` ya a la vista, se confirma que **hoy no existe ningún soporte para selección múltiple** en el motor real:

- `MotorNavegacion.paso(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado, Nodo nodoActual, String respuesta)` recibe un único `String`.
- `EstadoPaciente.registrarRespuesta(idNodo, respuesta)` guarda una sola respuesta por nodo (es un `LinkedHashMap<String,String>` indexado por `idNodo`) — si se marcaran dos opciones de la misma pregunta, solo una puede quedar registrada como "la" respuesta de ese nodo.

Es decir: la sobrecarga que Captura necesita no es una pieza que ya exista en Navegación esperando ser usada — es una extensión real por construir ahí. La dejo especificada con precisión, ya que ahora se ve exactamente dónde entra:

```java
public interface ServicioNavegacion {
    RecorridoDTO iniciar();
    RecorridoDTO responder(String recorridoId, String respuesta);       // ya existe, sin cambios
    RecorridoDTO responder(String recorridoId, List<String> respuestas); // nuevo
    RecorridoDTO consultar(String recorridoId);
}
```

Y, dentro de `MotorNavegacion`, un `paso` hermano del actual (o el mismo método sobrecargado) que resuelve antes de registrar nada:

```java
public ResultadoPaso paso(GrafoClinico grafo, BaseReglas base, EstadoPaciente estado,
        Nodo nodoActual, List<String> respuestasMarcadas) {
    String respuestaGanadora = respuestasMarcadas.size() == 1
            ? respuestasMarcadas.get(0)
            : respuestasMarcadas.stream()
                    .max(Comparator.comparingInt(r -> grafo.evaluarPaso(nodoActual, r).getPeso()))
                    .orElseThrow();
    return paso(grafo, base, estado, nodoActual, respuestaGanadora); // reusa el método existente tal cual
}
```

La ventaja de plantearlo así: el `paso(...)` de un solo `String` que ya existe **no cambia en absoluto** — el nuevo método solo decide cuál `String` ganador pasarle, reutilizándolo. Cero riesgo para el camino Sí/No y selección única, que hoy ya funciona y no debería tocarse. Esta extensión y su implementación siguen siendo responsabilidad del Módulo de Navegación, no de este documento — se deja aquí ya con la forma concreta que encajaría, para que quien la construya no tenga que adivinar el punto de entrada.

---

## 3. Responsabilidades del módulo (alcance actual)

**Sí hace:**

- Recibir la respuesta del cuidador para la pregunta actual: una sola opción (Sí/No, selección única) o varias (selección múltiple).
- Validar que cada opción marcada sea efectivamente una de las opciones válidas de la pregunta actual (defensa antes de llegar a Navegación).
- Registrar en un log de auditoría cada respuesta recibida: pregunta, tipo de interacción, opción(es) marcadas, timestamp — independiente de si el grafo avanza sin problema.
- Reunir todas las opciones marcadas y entregárselas **completas** a `ServicioNavegacion` (una sola con la firma actual, varias con la sobrecarga propuesta en la sección 2), sin decidir cuál de ellas domina.

**No hace (por ahora, a propósito):**

- **No resuelve el empate de una selección múltiple.** Cuál opción "gana" cuando se marca más de una es responsabilidad del Módulo de Navegación (sección 2) — Captura nunca compara pesos ni elige entre opciones.
- No recibe ni procesa imágenes tomadas por el cuidador, ni lecturas de sensores — ver sección 8.
- No evalúa reglas clínicas ni decide niveles de alerta — eso sigue siendo de `ServicioReglas`.
- No decide qué arista seguir ni mantiene el estado del recorrido — eso sigue siendo de `MotorNavegacion`/`ServicioNavegacion`/`SesionTriaje`.

---

## 4. Modelo de datos

```java
package org.signbb.backend.crobservaciones.modelo;

/** Cómo se le presenta la pregunta al cuidador y cómo debe interpretarse su respuesta. */
public enum TipoInteraccion {
    SI_NO,
    SELECCION_UNICA,
    SELECCION_MULTIPLE
}
```

```java
package org.signbb.backend.crobservaciones.modelo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * TAD SeleccionCuidador: lo que el cuidador marcó para la pregunta actual del
 * recorrido, antes de resolverse a la {@code condicion} única que
 * {@link org.signbb.backend.navegacion.servicios.ServicioNavegacion#responder}
 * espera. Para SI_NO y SELECCION_UNICA, {@code opcionesSeleccionadas} trae
 * exactamente un elemento; para SELECCION_MULTIPLE, uno o más.
 */
public final class SeleccionCuidador {

    private final String recorridoId;
    private final String idNodo;
    private final TipoInteraccion tipo;
    private final List<String> opcionesSeleccionadas;
    private final Instant timestamp;

    public SeleccionCuidador(String recorridoId, String idNodo, TipoInteraccion tipo,
            List<String> opcionesSeleccionadas) {
        if (recorridoId == null || recorridoId.isEmpty()) {
            throw new IllegalArgumentException("recorridoId debe ser una cadena no vacía");
        }
        if (idNodo == null || idNodo.isEmpty()) {
            throw new IllegalArgumentException("idNodo debe ser una cadena no vacía");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo debe ser TipoInteraccion");
        }
        if (opcionesSeleccionadas == null || opcionesSeleccionadas.isEmpty()) {
            throw new IllegalArgumentException("debe venir al menos una opción seleccionada");
        }
        if (tipo != TipoInteraccion.SELECCION_MULTIPLE && opcionesSeleccionadas.size() != 1) {
            throw new IllegalArgumentException(
                    "SI_NO y SELECCION_UNICA solo admiten exactamente una opción seleccionada");
        }
        this.recorridoId = recorridoId;
        this.idNodo = idNodo;
        this.tipo = tipo;
        this.opcionesSeleccionadas = List.copyOf(opcionesSeleccionadas);
        this.timestamp = Instant.now();
    }

    public String getRecorridoId() { return recorridoId; }
    public String getIdNodo() { return idNodo; }
    public TipoInteraccion getTipo() { return tipo; }
    public List<String> getOpcionesSeleccionadas() { return opcionesSeleccionadas; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeleccionCuidador)) return false;
        SeleccionCuidador otra = (SeleccionCuidador) o;
        return recorridoId.equals(otra.recorridoId) && idNodo.equals(otra.idNodo)
                && timestamp.equals(otra.timestamp);
    }

    @Override
    public int hashCode() { return Objects.hash(recorridoId, idNodo, timestamp); }

    @Override
    public String toString() {
        return "SeleccionCuidador(recorrido='" + recorridoId + "', nodo='" + idNodo + "', tipo=" + tipo
                + ", opciones=" + opcionesSeleccionadas + ")";
    }
}
```

---

## 5. Contrato del servicio: reunir y reenviar (sin resolver)

```java
package org.signbb.backend.crobservaciones.servicios;

import java.util.List;

import org.signbb.backend.crobservaciones.controladores.RecorridoDTO;
import org.signbb.backend.crobservaciones.modelo.TipoInteraccion;

/**
 * API del módulo de captura: recibe la(s) opción(es) que el cuidador marcó
 * para la pregunta actual, las valida y audita, y las entrega íntegras a
 * {@code ServicioNavegacion}. No decide cuál opción domina en una selección
 * múltiple — eso lo resuelve Navegación (sección 2).
 */
public interface ServicioCaptura {
    RecorridoDTO registrarRespuesta(String recorridoId, TipoInteraccion tipo, List<String> opcionesSeleccionadas);
}
```

```java
package org.signbb.backend.crobservaciones.servicios;

import java.util.List;

import org.signbb.backend.crobservaciones.controladores.RecorridoDTO;
import org.signbb.backend.crobservaciones.modelo.SeleccionCuidador;
import org.signbb.backend.crobservaciones.modelo.TipoInteraccion;
import org.signbb.backend.crobservaciones.repositorios.ObservacionEntity;
import org.signbb.backend.crobservaciones.repositorios.RepositorioObservaciones;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.springframework.stereotype.Service;

@Service
public class ServicioCapturaImpl implements ServicioCaptura {

    private final ServicioNavegacion servicioNavegacion;
    private final RepositorioObservaciones repositorioObservaciones;

    public ServicioCapturaImpl(ServicioNavegacion servicioNavegacion,
            RepositorioObservaciones repositorioObservaciones) {
        this.servicioNavegacion = servicioNavegacion;
        this.repositorioObservaciones = repositorioObservaciones;
    }

    @Override
    public RecorridoDTO registrarRespuesta(String recorridoId, TipoInteraccion tipo, List<String> opcionesSeleccionadas) {
        RecorridoDTO actual = servicioNavegacion.consultar(recorridoId);
        if (actual.pregunta() == null) {
            // el recorrido ya finalizó; no hay nada que registrar ni avanzar
            return actual;
        }
        String idNodo = actual.pregunta().id();

        SeleccionCuidador respuesta = new SeleccionCuidador(recorridoId, idNodo, tipo, opcionesSeleccionadas);

        List<String> opcionesValidas = actual.pregunta().opciones();
        for (String op : opcionesSeleccionadas) {
            if (!opcionesValidas.contains(op)) {
                throw new IllegalArgumentException(
                        "'" + op + "' no es una opción válida para la pregunta '" + idNodo + "'");
            }
        }

        // Se entrega todo lo marcado, tal cual: con una sola opción usa la firma
        // que ya existe hoy; con varias, la sobrecarga propuesta en la sección 2
        // (que resuelve internamente cuál domina, usando el peso por nodo).
        RecorridoDTO resultado = opcionesSeleccionadas.size() == 1
                ? servicioNavegacion.responder(recorridoId, opcionesSeleccionadas.get(0))
                : servicioNavegacion.responder(recorridoId, opcionesSeleccionadas);

        repositorioObservaciones.save(ObservacionEntity.desde(respuesta));
        return resultado;
    }
}
```

---

## 6. Persistencia (auditoría)

```java
package org.signbb.backend.crobservaciones.repositorios;

import java.time.Instant;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import java.util.List;

import org.signbb.backend.crobservaciones.modelo.SeleccionCuidador;
import org.signbb.backend.crobservaciones.modelo.TipoInteraccion;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Fila de auditoría por cada respuesta de texto recibida del cuidador. */
@Entity
@Getter
@NoArgsConstructor
public class ObservacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String recorridoId;
    private String idNodo;

    @Enumerated(EnumType.STRING)
    private TipoInteraccion tipo;

    @ElementCollection
    @CollectionTable(name = "observacion_opciones", joinColumns = @JoinColumn(name = "observacion_id"))
    private List<String> opcionesSeleccionadas;

    private Instant timestamp;

    public static ObservacionEntity desde(SeleccionCuidador r) {
        ObservacionEntity e = new ObservacionEntity();
        e.recorridoId = r.getRecorridoId();
        e.idNodo = r.getIdNodo();
        e.tipo = r.getTipo();
        e.opcionesSeleccionadas = r.getOpcionesSeleccionadas();
        e.timestamp = r.getTimestamp();
        return e;
    }
}
```

```java
package org.signbb.backend.crobservaciones.repositorios;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioObservaciones extends JpaRepository<ObservacionEntity, Long> {
    List<ObservacionEntity> findByRecorridoIdOrderByTimestampAsc(String recorridoId);
}
```

**Limitación a anotar:** este registro guarda lo que el cuidador marcó, pero no necesariamente cuál de esas opciones terminó "ganando" en una selección múltiple — porque esa decisión ahora vive dentro de Navegación y su resultado no queda expuesto de vuelta a Captura (`RecorridoDTO` solo trae la siguiente pregunta o el desenlace final, no qué arista se siguió en un paso intermedio). Si se necesita ese dato para trazabilidad clínica completa, hay que pedirle a Navegación que lo exponga (p. ej. agregando la condición efectivamente seguida a la respuesta de `responder(...)`, similar a como `ResultadoDTO.caminoCausal()` ya lo hace para el desenlace final) — queda como pregunta abierta en la sección 11, no como algo que Captura pueda resolver por su cuenta.

---

## 7. DTO y endpoint REST

Se extiende (no se reemplaza) el `RespuestaRequest` ya existente, agregando un campo opcional para el caso de selección múltiple:

```java
package org.signbb.backend.crobservaciones.controladores;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo de la petición que responde la pregunta actual.
 *
 * <p>Para SI_NO y SELECCION_UNICA, se sigue usando {@code respuesta} tal como
 * hoy. Para SELECCION_MULTIPLE, el cliente envía {@code opciones} con todas
 * las opciones marcadas (uno o más elementos) y deja {@code respuesta} nulo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaRequest {
    private String respuesta;
    private List<String> opciones;
}
```

Ya con `ControladorConsulta.java` a la vista, esto deja de ser una propuesta y pasa a ser un cambio concreto sobre código real. Hoy el controlador vive bajo `/api` y llama a `ServicioNavegacion` directo:

```java
@RestController
@RequestMapping("/api")
public class ControladorConsulta {

    @Autowired
    private ServicioNavegacion servicioNavegacion;

    @PostMapping("/recorridos/{recorridoId}/respuestas")
    public ResponseEntity<RecorridoDTO> responder(@PathVariable String recorridoId,
                                                  @RequestBody RespuestaRequest request) {
        return ResponseEntity.ok(servicioNavegacion.responder(recorridoId, request.getRespuesta()));
    }
    // iniciar() y consultar() no cambian
}
```

Es decir, la ruta real es `POST /api/recorridos/{recorridoId}/respuestas` (con el prefijo `/api`, que en la versión anterior de este documento no se había confirmado). El cambio mínimo es reemplazar la inyección de `ServicioNavegacion` por `ServicioCaptura` en este único método:

```java
@Autowired
private ServicioCaptura servicioCaptura; // en vez de ServicioNavegacion, solo para este endpoint

@PostMapping("/recorridos/{recorridoId}/respuestas")
public ResponseEntity<RecorridoDTO> responder(@PathVariable String recorridoId,
                                              @RequestBody RespuestaRequest request) {
    List<String> seleccionadas = request.getOpciones() != null
            ? request.getOpciones()
            : List.of(request.getRespuesta());
    TipoInteraccion tipo = request.getOpciones() != null
            ? TipoInteraccion.SELECCION_MULTIPLE
            : TipoInteraccion.SI_NO; // o SELECCION_UNICA — ver sección 2: hoy no hay forma de distinguirlas sin el dato en PreguntaDTO
    return ResponseEntity.ok(servicioCaptura.registrarRespuesta(recorridoId, tipo, seleccionadas));
}
```

`iniciar()` y `consultar()` no necesitan tocarse — siguen llamando a `ServicioNavegacion` directamente, porque no reciben una observación del cuidador. Esto deja explícito, otra vez, por qué la sección 2 es el bloqueo real: sin `tipoInteraccion` en `PreguntaDTO`, el backend no puede distinguir con certeza un Sí/No de una selección única de una sola opción — hoy se pueden confundir. No afecta el avance (ambas devuelven la única opción tal cual), pero sí afecta la auditoría (sección 6 registraría un tipo incorrecto).

---

## 8. Fuera de alcance por ahora (reconocido en la arquitectura, no implementado)

- **Imágenes tomadas por el cuidador** como evidencia adjunta a una respuesta (distinto de las imágenes de referencia que el sistema ya muestra, sección 0). Cuando se aborde, encaja como un método adicional en `ServicioCaptura` (`registrarRespuestaConImagen(...)`) y un puerto `AlmacenMultimedia` para decidir dónde se guardan los archivos.
- **Lecturas de sensores** (termómetro, oxímetro, etc.) traducidas a una condición de arista mediante una tabla de umbrales. Cuando se aborde, encaja como un método adicional (`registrarLecturaSensor(...)`) y un `TraductorSensor` con una tabla de umbrales por tipo de sensor (los de temperatura ya están definidos en la Base de Signos y Reglas: Fiebre ≥38.0°C, Frío <35.5°C).

Ninguno de estos dos puntos requiere rediseñar lo que ya está en las secciones 3 a 7 — se agregan como extensiones, no como reemplazos.

---

## 9. Manejo de errores y validaciones

- **Opción no válida:** si alguna de las `opcionesSeleccionadas` no está en `actual.pregunta().opciones()`, se rechaza con `400 Bad Request` antes de tocar `ServicioNavegacion` o la auditoría.
- **Recorrido ya finalizado:** si `servicioNavegacion.consultar(recorridoId)` devuelve `pregunta() == null` (estado `FINALIZADO`, mismo patrón que ya usa `ServicioNavegacionImpl.responder`), se devuelve ese `RecorridoDTO` tal cual, sin registrar nada nuevo.
- **Lista de opciones vacía:** rechazada por el constructor de `SeleccionCuidador` (`IllegalArgumentException` → `400`).
- **SI_NO/SELECCION_UNICA con más de una opción:** rechazada por el mismo constructor — es una señal de que el cliente está mal armado (le envió varias opciones a una pregunta que no admite selección múltiple).

---

## 10. Paso a paso de implementación

1. Acordar con quien lleve el Módulo de Navegación la sobrecarga `responder(String recorridoId, List<String> respuestas)` (sección 2) y, si se decide agregar, el campo `tipoInteraccion` en la metadata del nodo/`Nodo`/`PreguntaDTO`. Este acuerdo bloquea el resto — sin él, Captura no tiene ni cómo enviar varias opciones a la vez ni cómo saber que debe recolectarlas.
2. Crear `crobservaciones.modelo` con `TipoInteraccion` y `SeleccionCuidador` (sección 4), con sus pruebas unitarias de validación.
3. Crear `crobservaciones.repositorios` con `ObservacionEntity` y `RepositorioObservaciones` (sección 6).
4. Crear `ServicioCaptura`/`Impl` (sección 5), inyectando `ServicioNavegacion` (usando la sobrecarga nueva solo cuando haya más de una opción marcada).
5. Extender `RespuestaRequest` (sección 7) y cablear `ControladorConsulta` para que llame a `ServicioCaptura.registrarRespuesta` en vez de a `ServicioNavegacion.responder` directamente — así toda respuesta queda auditada, incluidas las de un solo valor.
6. Pruebas de integración end-to-end contra `ContextoClinico` cargado de los JSON de ejemplo: un recorrido real donde una pregunta de selección múltiple con dos opciones marcadas termine avanzando hacia el nodo destino de mayor `peso` (esto prueba principalmente la sobrecarga de Navegación, pero Captura debe tener un caso que confirme que le llegan y le reenvía las dos opciones sin alterarlas).
7. Solo después de validar lo anterior, decidir si vale la pena abordar imágenes de cuidador y/o sensores (sección 8) para este entregable de tesis, o dejarlas documentadas como trabajo futuro.

---

## 11. Supuestos y preguntas abiertas

1. **Confirmado con el código real de `MotorNavegacion`: la sobrecarga para selección múltiple no existe todavía** — hoy `paso(...)` solo acepta un `String` y `EstadoPaciente` solo guarda una respuesta por nodo. No es una pieza ya construida esperando que Captura la use; es trabajo pendiente del Módulo de Navegación, con la forma concreta propuesta en la sección 2.
2. **El campo `tipoInteraccion` sigue sin existir** en la metadata del grafo (confirmado con el JSON real: solo trae `tipo`, `peso`, `archivos`, `src`, `categorias`). Sigue pendiente decidir si se agrega ahí (recomendado) o se maneja de otra forma.
3. **Trazabilidad de cuál opción ganó en una selección múltiple** (nota al final de la sección 6): hoy Captura no puede auditarlo porque `RecorridoDTO` no expone la condición seguida en un paso intermedio (`ResultadoPaso` solo trae `caminoCausal` para el desenlace final, no para cada paso EN_CURSO). Si el equipo lo necesita, es un pedido a hacerle a Navegación, no algo que este módulo pueda resolver solo.
4. **Regla de desempate cuando dos nodos destino candidatos tienen el mismo `peso`** (sección 2): la propuesta de código usa `max(...)`, que en caso de empate exacto devuelve el primero encontrado en orden de iteración — no hay una regla explícita de desempate más allá de eso. A confirmar si hace falta una más determinista.
