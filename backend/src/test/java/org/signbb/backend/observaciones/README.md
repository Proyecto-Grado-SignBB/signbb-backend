# Pruebas del módulo de observaciones {#pruebas_observaciones}

Pruebas del paquete `org.signbb.backend.observaciones`, el que **valida, audita
y reenvía** la respuesta del cuidador hacia el motor de navegación. Son
**JUnit 5 + Mockito**, sin Spring, base de datos ni Redis.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `servicios` | `ServicioCapturaImplTest` | 15 | valida la selección contra la pregunta actual, audita la observación (con el padre que respondió), reenvía al servicio de navegación y guarda "todo o nada" con candado por recorrido (también al retroceder) y rechaza con 409 una respuesta para una pregunta que ya no es la actual |
| `servicios` | `ServicioRecorridosImplTest` | 20 | el recorrido queda a nombre del padre, otro padre recibe 404 y el estado y resultado se guardan al terminar o retroceder; un recorrido sin sesión queda vencido (410) y reiniciar lo marca abandonado y empieza otro |
| `modelo` | `SeleccionCuidadorTest` | 6 | validación del constructor de `SeleccionCuidador` según el tipo de interacción |
| `controladores` | `ControladorConsultaTest` | 15 | una respuesta vacía da 400 (no 500), una respuesta válida llega al servicio, una de más de 200 caracteres da 422, un recorrido ocupado da 409 al responder y al retroceder, el id de la pregunta llega al servicio y una pregunta que ya no es la actual da 409; un recorrido vencido da 410, reiniciar devuelve el nuevo y reiniciar uno terminado da 409 |

En total son **56 pruebas**. La regla de dueño también se prueba con la cadena
de seguridad real en `seguridad/ReglasAccesoIntegracionTest`.

## Qué se prueba y por qué importa

### Captura de la respuesta (`ServicioCapturaImplTest`)
Con `ServicioNavegacion` y `RepositorioObservaciones` **simulados con Mockito**:

- una sola opción se valida, se **audita** (`ObservacionEntity` guardada) y se
  reenvía al motor, devolviendo su resultado;
- una **selección múltiple** se reenvía completa: la captura **nunca decide**
  cuál opción domina (eso es del motor);
- una opción que **no pertenece** a la pregunta actual se rechaza **antes** de
  tocar el motor y **sin** guardar observación;
- si el recorrido ya está `FINALIZADO`, no se responde ni se audita;
- **todo o nada**: la sesión en Redis, la observación y la fila del recorrido
  se guardan dentro de una sola transacción de Postgres. Si falla la
  observación, el guardado del recorrido o el cierre de la transacción, se
  revierte Postgres y la sesión de Redis vuelve a la copia tomada antes; si
  tampoco se puede devolver la sesión, sale el error original;
- **pregunta esperada**: si la petición trae el id de la pregunta que contesta
  y ya no es la actual, se rechaza con `PreguntaDesactualizadaException` (409)
  sin registrar ni avanzar nada; sin id funciona como antes, y si el recorrido
  ya terminó se devuelve tal cual;
- **candado ocupado**: si otra operación tiene el candado del recorrido, no se
  toca nada (ni navegación, ni sesión, ni transacción);
- **retroceder** sigue las mismas reglas (transacción, devolver la sesión si falla).

### Dueño del recorrido (`ServicioRecorridosImplTest`)
Con `ServicioNavegacion` y `RepositorioRecorridos` simulados:

- `iniciar` guarda la fila `recorrido` con el id del padre;
- el dueño pasa; otro padre o un id inexistente reciben **404** con el mismo
  mensaje, para no revelar que el recorrido existe;
- al llegar a `FINALIZADO` se guardan el nivel y el mensaje; al retroceder
  desde el final se borran y vuelve a `EN_CURSO`;
- "Mis recorridos" lista solo los del padre; los que siguen en curso sin
  sesión en Redis (venció a las 8 h) pasan a `VENCIDO`, y si Redis no responde
  la lista sale igual;
- **vigente** (CU10): un recorrido en curso sin sesión se marca `VENCIDO` y da
  410; uno reiniciado (`ABANDONADO`) da 410 con su propio mensaje; uno
  terminado pasa;
- **reiniciar** (CU11): con el candado del recorrido, lo marca `ABANDONADO`,
  inicia uno nuevo en la misma transacción y después borra la sesión vieja (si
  Redis falla en ese paso solo se registra); uno terminado da 409 y uno ya
  reiniciado da 410.

### Validación de la selección (`SeleccionCuidadorTest`)
`SeleccionCuidador` es el value object que normaliza lo que marcó el cuidador
según el `TipoInteraccion`:

- `SI_NO` y `SELECCION_UNICA` exigen una sola opción;
- `SELECCION_MULTIPLE` acepta varias;
- se rechazan la lista vacía y el `recorridoId` vacío.

## Cómo ejecutarlas

    ./mvnw test -Dtest='org.signbb.backend.observaciones.**'

No requieren PostgreSQL ni Redis.

> La **auditoría** de observaciones vive en este módulo (`ServicioCaptura`). La
> autoría de reglas, con su CRUD y publicación, está en \ref pruebas_autoria.
