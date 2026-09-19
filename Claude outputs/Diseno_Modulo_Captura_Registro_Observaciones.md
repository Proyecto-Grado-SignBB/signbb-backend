# Módulo de Captura y Registro de Observaciones — Diseño

Proyecto SignBB (ISIST-2610-20). Este documento cubre la fase de **diseño**, antes de programar: qué debe tener el módulo, sus entidades, sus contratos hacia los módulos ya construidos (Motor de Reglas, Motor de Navegación) y una propuesta de fases de implementación.

## 1. Responsabilidad del módulo (según el diagrama de contenedores)

> "Maneja el recorrido del grafo y revisa si se han cumplido las condiciones para disparar reglas."

Recibe la observación que el cuidador reporta sobre el bebé (`observación de cuidador [JSON/HTTP]`, ya autenticada por el Módulo de Seguridad), y a partir de ella:

1. Le pide al **Motor de Reglas** que evalúe el camino recorrido hasta ahora (`Solicita evaluación de camino recorrido [JSON/HTTP]`).
2. Le pide al **Motor de Navegación** cuál es el siguiente paso del grafo clínico (`Solicita siguiente paso [JSON/HTTP]`).
3. Persiste lo ocurrido durante la consulta y, si aplica, lee/escribe multimedia y notifica cambios hacia afuera del contenedor.

Es, en otras palabras, el **orquestador de la consulta**: no decide clínicamente nada por sí mismo (eso es el Motor de Reglas) ni decide qué preguntar después (eso es el Motor de Navegación) — coordina ambos y deja constancia de lo que pasó.

## 2. Lo que falta pensar: sub-responsabilidades internas

Esto es lo que el diagrama de contenedores no muestra (queda "dentro de la caja azul") y conviene decidir antes de programar:

**a. Gestión del ciclo de vida de la consulta**
Una consulta es una sesión: empieza cuando el cuidador dice "Comenzar consulta" (pantalla `Inicio.dc.html`), recorre un camino en el grafo, y termina cuando el Motor de Navegación indica que no hay más pasos, o cuando el Motor de Reglas dispara un nivel `CONSULTAR_MEDICO`/`URGENCIA` que corta el flujo. El módulo necesita saber en qué consulta y en qué nodo del grafo está el cuidador en cada petición.

**b. Recepción y validación de la observación**
La observación que llega depende del tipo de pregunta (`Sí/No`, `Selección única`, `Selección múltiple`). Para selección múltiple, el módulo debe aplicar la regla de peso/gravedad que ya definimos en la base de signos: si el cuidador marca varias opciones a la vez, se queda con la de mayor peso para decidir qué le pasa al Motor de Reglas/Navegación como "signo observado". Esta resolución de empate es lógica de este módulo, no del Motor de Reglas.

**c. Persistencia de cada observación (trazabilidad clínica)**
Cada observación registrada debe quedar guardada con su consulta, el nodo/signo al que corresponde, la respuesta dada, y el momento en que se dio — no solo para la lógica del grafo, sino como historial clínico auditable (importante para un dispositivo/software de apoyo diagnóstico).

**d. Orquestación y corte de flujo**
Tras registrar la observación: primero se consulta el Motor de Reglas. Si el resultado es `CONSULTAR_MEDICO` o `URGENCIA`, hay que decidir si la consulta se detiene ahí (mostrando la acción recomendada) o si igual se le pregunta al Motor de Navegación por more contexto. Si el resultado es `NORMAL`, se continúa pidiendo el siguiente paso al Motor de Navegación con normalidad. Esta decisión (¿se corta o no se corta el flujo?) hay que definirla explícitamente — no está en el diagrama.

**e. Manejo de multimedia**
Varios signos de la base (cianosis, palidez, ictericia, distensión abdominal) son de tipo "Comparación de imágenes": el cuidador puede necesitar subir o ver una foto de referencia. El módulo lee/escribe multimedia (`HTTP/HTTPS`) — probablemente contra un storage de objetos o el sistema de archivos del backend, no la base de datos relacional.

**f. Notificación de cambios**
La flecha `Notifica cambios [RESP]` que sale del contenedor sugiere que, además de responder síncronamente a la petición HTTP del cuidador, el módulo notifica hacia afuera (a la app, o a un canal de seguimiento) cuando algo relevante cambia — típicamente cuando se dispara un nivel de alerta. Falta decidir si esto es solo la respuesta HTTP normal o si hace falta algo adicional (WebSocket, push notification) — ver pregunta abierta al final.

**g. Manejo de errores y validaciones**
Casos que el diseño debe cubrir explícitamente: sesión de consulta inexistente o ya cerrada, observación fuera de las opciones válidas para ese nodo, edad del bebé fuera del rango al que aplica una regla, doble envío de la misma observación (idempotencia).

## 3. Componentes internos propuestos

Dentro de "Módulo de Captura y Registro de Observaciones" (un único Spring Boot module/paquete):

| Componente | Tipo | Responsabilidad |
|---|---|---|
| `ObservacionController` | `@RestController` | Expone los endpoints REST hacia el cuidador (vía Módulo de Seguridad). |
| `ConsultaService` | `@Service` | Orquesta: valida la observación, la persiste, llama a `RuleEngineClient` y a `NavigationEngineClient`, decide si se corta el flujo. |
| `ObservacionValidator` | Componente de dominio | Valida el tipo de respuesta según el nodo (Sí/No, única, múltiple+peso) y resuelve el desempate de selección múltiple. |
| `ConsultaRepository` / `ObservacionRepository` | `@Repository` (Spring Data JPA) | Persisten `SesionConsulta` y `Observacion` en PostgreSQL. |
| `MultimediaService` | `@Service` | Lee/escribe evidencia multimedia asociada a una observación. |
| `RuleEngineClient` | Cliente interno | Encapsula la llamada JSON/HTTP al Motor de Reglas ("Solicita evaluación de camino recorrido"). |
| `NavigationEngineClient` | Cliente interno | Encapsula la llamada JSON/HTTP al Motor de Navegación ("Solicita siguiente paso"). |
| `NotificacionService` | `@Service` | Encapsula la notificación de cambios hacia afuera del contenedor. |

## 4. Entidades propuestas (coherentes con `Nodo` / `ReglaClinica` / `NivelAlerta` ya existentes)

- **`SesionConsulta`**: id, bebé/cuidador asociado, nodo actual (`Nodo`), estado (`EN_CURSO`, `FINALIZADA_NORMAL`, `FINALIZADA_CONSULTAR_MEDICO`, `FINALIZADA_URGENCIA`), fecha de inicio/fin.
- **`Observacion`**: id, `sesionConsulta`, `nodo` (el signo preguntado), valor(es) reportado(s) por el cuidador, peso resuelto (si aplica selección múltiple), timestamp, referencia a evidencia multimedia (opcional).
- **`EvidenciaMultimedia`** (opcional, fase 3): id, `observacion`, URL/ruta del archivo, tipo (foto), timestamp.

Estas conviven con `Nodo`, `ReglaClinica` y `NivelAlerta` que ya existen del Motor de Reglas/Navegación — este módulo no las duplica, las referencia.

## 5. Endpoints propuestos

```
POST   /consultas                          → inicia una SesionConsulta, devuelve el primer nodo/pregunta
POST   /consultas/{id}/observaciones       → registra la observación del cuidador para el nodo actual
                                              body: { nodoId, valores: [...] }
                                              → internamente: valida → persiste → llama Motor de Reglas
                                                → si NORMAL: llama Motor de Navegación → devuelve siguiente nodo
                                                → si CONSULTAR_MEDICO/URGENCIA: corta el flujo → devuelve nivel + acción
POST   /consultas/{id}/multimedia          → sube evidencia (foto) asociada a la observación actual
GET    /consultas/{id}                     → estado actual de la consulta (para reanudar si se cierra la app)
```

## 6. Fases de implementación propuestas

1. **Fase 1 — Camino feliz (MVP):** `ConsultaService` + `ObservacionController` + persistencia básica (`SesionConsulta`, `Observacion`) + `RuleEngineClient` y `NavigationEngineClient` contra el Motor de Reglas/Navegación reales, sin multimedia, sin selección múltiple con peso (asumiendo por ahora solo Sí/No y selección única).
2. **Fase 2 — Selección múltiple con peso:** `ObservacionValidator` con la lógica de desempate por peso/gravedad para signos de tipo selección múltiple.
3. **Fase 3 — Multimedia:** `MultimediaService` + endpoint de subida + asociación a `Observacion`.
4. **Fase 4 — Notificaciones y corte de flujo:** `NotificacionService`, y la lógica explícita de qué pasa cuando el Motor de Reglas devuelve `CONSULTAR_MEDICO` o `URGENCIA` (cortar vs. continuar, qué se le muestra al cuidador).
5. **Fase 5 — Errores y robustez:** validaciones (sesión inexistente/cerrada, observación inválida, idempotencia), pruebas unitarias e de integración.

## 7. Preguntas abiertas antes de implementar

1. **Contrato real del Motor de Reglas y del Motor de Navegación**: ¿ya tienen definidas sus interfaces/DTOs (nombres de clases, endpoints, forma del JSON)? Si existen, lo ideal es que me los compartas (código o especificación) para que `RuleEngineClient`/`NavigationEngineClient` calcen exactamente, en vez de que yo proponga una interfaz que después haya que adaptar.
2. **"Notifica cambios [RESP]"**: ¿es solo la respuesta HTTP normal al cuidador, o hace falta notificar a alguien más (ej. un cuidador secundario, un profesional de salud) en tiempo real?
3. **Corte de flujo**: cuando se dispara `CONSULTAR_MEDICO` o `URGENCIA`, ¿la consulta siempre termina ahí, o hay casos en los que se sigue preguntando igual?
