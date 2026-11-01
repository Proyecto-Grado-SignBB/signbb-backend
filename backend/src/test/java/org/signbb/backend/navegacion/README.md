# Pruebas del módulo de navegación {#pruebas_navegacion}

> Índice general de la suite: \ref pruebas.

Pruebas unitarias del paquete `org.signbb.backend.navegacion`: los TADs del
recorrido, la lectura del contenido clínico, el motor de navegación y el
servicio que usa la API REST. Son JUnit 5 **puro** (sin Spring, sin base de
datos y sin Redis), así que corren igual en local y en GitHub Actions.

## Enfoque

Desde el 31-oct-2026 la suite se enfoca en el **comportamiento esperado de los
CU01, CU03, CU08 y CU09** y en las excepciones que el SRS documenta. Se
retiraron los bordes técnicos que no cambian el comportamiento observable:
empates de la cola, destinos repetidos, formatos legados, copias defensivas y
variantes de configuración inválida.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `modelo` | `EstadoPacienteTest` | 5 | memoria de trabajo: respuestas, puntaje y pila de pasos |
| `modelo` | `EstadoPacienteNavegacionTest` | 5 | cola, ruta, foto y restauración al retroceder |
| `modelo` | `GrafoClinicoTest` | 10 | nodos, aristas, rutas, ciclos, paso, camino causal y tipo de interacción |
| `modelo` | `NodoTest`, `RespuestaTest` | 2 + 1 | validación e igualdad de nodos y aristas |
| `modelo` | `ResultadoPasoRecomendacionesTest` | 1 | recomendaciones con su camino |
| `modelo` | `ContextoClinicoTest` | 4 | versiones del contenido clínico en memoria |
| `datos.serializacion` | `GrafoClinicoMapperTest` | 7 | JSON ↔ TAD del grafo y rechazo de referencias inválidas |
| `datos.serializacion` | `CompositorGrafoClinicoTest` | 10 | composición raíz + subgrafos, prefijo de ids y reglas |
| `datos.serializacion` | `JsonParserTest` | 5 | reglas en FND y rechazo de destinos inválidos |
| `motor` | `MotorNavegacionTest` | 7 | recorrido, alertas y respuesta inválida |
| `motor` | `MotorNavegacionRetrocederTest` | 4 | volver a la pregunta anterior y deshacer reglas |
| `motor` | `MotorNavegacionLimiteTest` | 3 | máximo de preguntas y alerta que gana al límite |
| `motor` | `MotorNavegacionColaTest` | 10 | cola de selección múltiple, alertas y retroceso |
| `motor` | `OrdenColaTest` | 2 | gravedad primero y mejor opción |
| `servicios` | `ServicioNavegacionImplTest` | 10 | iniciar, responder, consultar, retroceder, resultado y 404 |
| `servicios` | `ServicioNavegacionVersionesTest` | 3 | un recorrido conserva su versión del contenido |

En total son **89 pruebas**. Las pruebas de la sesión en Redis
(`cache/RepositorioSesionRedisTest` y `cache/ColaSeleccionMultipleRedisIntegracionTest`)
están documentadas en [`backend/src/test/README.md`](../../../../../README.md).

## Fixtures

- **Contenido del sistema.** Es el que usan `MotorNavegacionTest`,
  `MotorNavegacionRetrocederTest` y `ServicioNavegacionImplTest`:
  `ContenidoEjemplo.compuesto()`, que une la raíz real del sistema
  (`src/main/resources/static/grafo_sistema/grafo_raiz.json`) con los cuatro
  subgrafos y reglas del seed de demo (`src/main/resources/seed/demo/`).
  `DatabaseInit` siembra la raíz; los subgrafos se publican con el seed de demo.
  El camino que más se prueba es
  `pregunta_inicial → Actividad y color → Revisar actividad y color →
  q_neuro_gate`.
- **Grafos mínimos armados en código.** Los usan `MotorNavegacionLimiteTest`
  (una cadena de 12 preguntas), `MotorNavegacionColaTest` y `OrdenColaTest`.

## Qué se prueba y por qué importa

### CU01/CU03: recorrido y resultado (`MotorNavegacionTest`)
- El recorrido cruza de la raíz al subgrafo y una respuesta sin signos de
  alarma cierra en **NORMAL**.
- Cada regla clínica lleva a su desenlace: un signo de alarma da URGENCIA y
  uno de alerta media da CONSULTAR_MEDICO.
- Una respuesta que no es opción se rechaza antes de tocar el estado, como
  única prueba representativa de entrada inválida.

### RF21: retroceder (`MotorNavegacionRetrocederTest`)
- Vuelve a la pregunta anterior, borra solo esa respuesta y permite tomar otro
  camino.
- Deshace solo las reglas de la respuesta descartada y permite volver a
  dispararlas.
- Se puede retroceder varias veces seguidas; sin respuestas es un error 400.

### Límite de preguntas (`MotorNavegacionLimiteTest`)
- Al llegar a la pregunta número 10 el recorrido cierra en **NORMAL** con el
  nodo `sistema/limite_preguntas`.
- Una regla en la décima respuesta gana al límite: una alerta nunca queda
  tapada por el corte.

### Cola de selección múltiple (`MotorNavegacionColaTest`, `OrdenColaTest`)
- Se sigue a fondo la rama más grave y las demás quedan pendientes.
- Las recomendaciones conservan su camino causal.
- Una alerta termina el recorrido y descarta la cola.
- Se puede retroceder desde el resultado final y desde la selección múltiple.

### Servicio (`ServicioNavegacionImplTest`, `ServicioNavegacionVersionesTest`)
- `iniciar` abre un recorrido EN_CURSO en `pregunta_inicial`.
- Cada respuesta y cada retroceso quedan guardados y `consultar` los refleja.
- Una selección múltiple con un signo de alarma cierra en URGENCIA.
- Un recorrido FINALIZADO no acepta más respuestas y sigue devolviendo su
  desenlace; un id inexistente da 404.
- Un recorrido en curso conserva la versión del contenido con la que empezó.

## Qué se quitó en la reorganización

| Archivo | Motivo |
|---|---|
| `MotorNavegacionNormalizacionTest` + `ejemplo_*_norm.json` | Grafo de normalización de bases de datos, no clínico. |
| `MotorNavegacionSeguridadTest` + `ejemplo_*_sec.json` | Grafo de ciberseguridad, no clínico. |
| `MotorNavegacionSeleccionMultipleTest` | Duplicado por `MotorNavegacionColaTest`. |
| `modelo/ContextoTest` (7) | El TAD `Contexto` no tiene referencias en `src/main`. |
| Bordes técnicos del 31-oct-2026 | Empates de cola, destinos repetidos, preguntas repetidas, umbrales combinados, reglas compuerta, formatos legados, copias defensivas y variantes de configuración inválida. |

## Cómo ejecutarlas

Desde `backend/`:

    ./mvnw test -Dtest='org.signbb.backend.navegacion.**'   # solo el módulo
    ./mvnw verify                                           # suite completa, como el CI

## Resultados

Corrida histórica del workflow `build` sobre el commit `518f775`:
[run 37948316527](https://github.com/Proyecto-Grado-SignBB/signbb-backend/actions/runs/37948316527).
**BUILD SUCCESS.**

Tras el recorte del 31-oct-2026, el módulo tiene **89 pruebas** y la suite
completa del backend **364**.
