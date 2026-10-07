# Pruebas del módulo de navegación

Pruebas unitarias del paquete `org.signbb.backend.navegacion`: los TADs del
recorrido, la lectura del contenido clínico, el motor de navegación y el
servicio que usa la API REST. Son JUnit 5 **puro** (sin Spring, sin base de
datos y sin Redis), así que corren igual en local y en GitHub Actions.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `modelo` | `EstadoPacienteTest` | 10 | memoria de trabajo: respuestas, puntaje y la pila de pasos para retroceder |
| `modelo` | `EstadoPacienteNavegacionTest` | 10 | cola de opciones pendientes, ruta, recomendaciones y su foto para deshacer |
| `modelo` | `GrafoClinicoTest` | 25 | TAD del grafo: nodos, aristas, rutas válidas, ciclos, camino causal y tipo de interacción |
| `modelo` | `NodoTest`, `RespuestaTest` | 6 + 4 | validación e igualdad de nodos y aristas |
| `modelo` | `ContextoTest` | 7 | historial de sesiones de un paciente |
| `modelo` | `ResultadoPasoRecomendacionesTest` | 3 | recomendaciones del resultado de un paso |
| `datos.serializacion` | `GrafoClinicoMapperTest` | 15 | JSON ↔ TAD del grafo; rechazo de contenido inválido |
| `datos.serializacion` | `CompositorGrafoClinicoTest` | 14 | composición raíz + subgrafos y prefijo de ids y reglas |
| `datos.serializacion` | `JsonParserTest` | 11 | lectura de reglas en FND y rechazo de reglas mal formadas |
| `motor` | `MotorNavegacionTest` | 12 | recorrido sobre el contenido clínico real: alertas por reglas y validación de respuestas |
| `motor` | `MotorNavegacionRetrocederTest` | 9 | volver a la pregunta anterior con la pila de pasos |
| `motor` | `MotorNavegacionLimiteTest` | 8 | máximo de preguntas (10 por defecto) |
| `motor` | `MotorNavegacionColaTest` | 23 | cola de la selección múltiple: ramas, alertas, umbral, reglas y retroceder |
| `motor` | `OrdenColaTest` | 7 | orden de la cola: gravedad, peso y antigüedad |
| `servicios` | `ServicioNavegacionImplTest` | 9 | la fachada de la API: iniciar, responder, consultar, retroceder y errores |

En total son **173 pruebas**. Las pruebas de la sesión en Redis
(`cache/RepositorioSesionRedisTest` y `cache/ColaSeleccionMultipleRedisIntegracionTest`)
están documentadas en [`backend/src/test/README.md`](../../../../../README.md).

## Fixtures

- **Contenido clínico real.** Es el que usan `MotorNavegacionTest`,
  `MotorNavegacionRetrocederTest` y `ServicioNavegacionImplTest`:
  `ContenidoEjemplo.compuesto()`, que une el grafo raíz con el subgrafo de
  ictericia y sus reglas, tal como lo siembra `DatabaseInit`. El camino que
  se prueba es `pregunta_inicial → puente_piel → pregunta_coloracion →
  pregunta_signos`. En las reglas, R1 (coloración + somnolencia) da URGENCIA,
  R2 (come menos + el amarillo se extiende) da URGENCIA y R3 (el amarillo se
  extiende) da CONSULTAR_MEDICO.
- **Grafos mínimos armados en código.** Los usan `MotorNavegacionLimiteTest`
  (una cadena de 12 preguntas), `MotorNavegacionColaTest` y `OrdenColaTest`.
  Sirven para casos que el contenido real no muestra, como recorridos largos o
  varias ramas pendientes.

## Qué se prueba y por qué importa

### Alertas y validación (`MotorNavegacionTest`)
- El recorrido cruza de la raíz al subgrafo y una respuesta sin signos de
  alarma cierra en **NORMAL**.
- Cada regla clínica lleva a su desenlace. Si se disparan dos reglas a la vez
  (R2 y R3), **gana el nivel más grave**: el resultado nunca es menos grave de
  lo que indica la evidencia.
- Una respuesta inválida (que no es una opción de la pregunta, varias
  opciones en una pregunta de selección única, una opción repetida o una
  respuesta sobre un terminal) se **rechaza antes de tocar el estado**. Así
  no quedan hechos falsos que después disparen reglas.

### Retroceder (`MotorNavegacionRetrocederTest`)
Cada respuesta apila un paso con la pregunta, las opciones y las reglas que
disparó. Retroceder lo desapila.
- Vuelve a la pregunta anterior, borra solo esa respuesta y permite tomar
  otro camino.
- **Deshace solo las reglas de la respuesta descartada.** Después de
  retroceder desde una URGENCIA, responder "NINGUNO" da NORMAL, y volver a
  marcar el signo dispara la regla de nuevo.
- **No reevalúa las reglas.** Se comprueba con un *spy* que `evaluar` no se
  llama al retroceder.
- Se puede retroceder varias veces seguidas. Retroceder sin respuestas es un
  error de la petición (`IllegalArgumentException`, HTTP 400).
- **Contenido cambiado.** Si la pregunta a la que se vuelve ya no existe en
  el contenido clínico, se lanza `IllegalStateException` en lugar de
  devolver un nodo inexistente.
- **Sesiones guardadas antes de existir la pila.** Se reproduce el recorrido
  y la pila queda reconstruida. Si el contenido cambió, también se lanza
  `IllegalStateException`.

### Límite de preguntas (`MotorNavegacionLimiteTest`)
- Cuando el cuidador llega a la pregunta número 10, el recorrido cierra en
  **NORMAL** con el nodo de sistema `sistema/limite_preguntas`, para que
  ninguna sesión quede abierta indefinidamente.
- Una regla o un terminal en la décima respuesta le ganan al límite. Es
  decir, una alerta nunca queda tapada por el corte.
- El límite se puede configurar (`signbb.navegacion.max-preguntas`) y un valor
  menor que 1 se rechaza.
- **Las respuestas deshechas no cuentan.** Retroceder y volver a responder no
  acerca el corte.
- Desde el cierre por límite se puede retroceder a la décima pregunta.

### Cola de la selección múltiple (`MotorNavegacionColaTest`, `OrdenColaTest`)
- Se sigue a fondo la opción más grave y las demás quedan pendientes.
- Una alerta anula las recomendaciones de ramas anteriores.
- No se repiten destinos ni preguntas.
- Se prueba la interacción de la cola con el umbral, con las reglas compuerta
  y con retroceder.

### Servicio (`ServicioNavegacionImplTest`)
- `iniciar` abre un recorrido EN_CURSO en `pregunta_inicial`.
- Cada respuesta y cada retroceso **quedan guardados**: `consultar` los
  refleja y la siguiente respuesta parte de ese estado.
- Una selección múltiple con un signo de alarma cierra en URGENCIA.
- **Un recorrido FINALIZADO no acepta más respuestas** y sigue devolviendo
  su desenlace.
- **Errores.** Un id inexistente da `RecursoNoEncontradoException` (HTTP 404)
  y una lista de respuestas vacía se rechaza.

## Qué se quitó en la reorganización

| Archivo | Motivo |
|---|---|
| `MotorNavegacionNormalizacionTest` + `ejemplo_*_norm.json` | Grafo de normalización de bases de datos, no clínico. La regla compuerta ya se prueba en `MotorNavegacionColaTest`. |
| `MotorNavegacionSeguridadTest` + `ejemplo_*_sec.json` | Grafo de ciberseguridad, no clínico. El desempate y las reglas AND/OR ya están en `OrdenColaTest`, `MotorNavegacionTest` (R2/R3) y `JsonParserTest`. |
| `MotorNavegacionSeleccionMultipleTest` | Escrito contra firmas "inferidas" y duplicado por `MotorNavegacionColaTest`. |
| `motor/README.md`, `docs/img/grafo_norm.svg`, `docs/img/grafo_sec.svg` | Solo documentaban los dos ejemplos anteriores. |
| Dos tests de retroceder solapados en `MotorNavegacionTest` | Se fundieron en uno al pasar a `MotorNavegacionRetrocederTest`. |

Los tests que estaban en el paquete viejo `org.signbb.backend.model` se
movieron a `navegacion.modelo` y `navegacion.datos.serializacion` sin cambiar
su contenido. Los de reglas (`BaseReglasTest` y `ReglaClinicaTest`) siguen en
`model/`.

Se descartó un caso: restaurar las respuestas previas cuando una regla
compuerta vuelve a preguntar algo ya respondido. Con la cola, el motor ya no
vuelve a preguntar una pregunta respondida (sigue las respuestas guardadas),
así que ese caso ya no se alcanza por el motor. Lo que sí se mantiene probado
a nivel del TAD es que restaurar las previas funciona, en
`EstadoPacienteTest.desapilarRestauraLasRespuestasPrevias`.

## Cómo ejecutarlas

Desde `backend/`:

    ./mvnw test -Dspring.profiles.active=dev -Dtest='org.signbb.backend.navegacion.**'   # solo el módulo
    ./mvnw verify -Dspring.profiles.active=dev                                           # suite completa, como el CI

## Resultados en GitHub Actions

_Pendiente: se completa con el run del CI._
