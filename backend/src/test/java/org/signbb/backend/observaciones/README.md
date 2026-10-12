# Pruebas del módulo de observaciones {#pruebas_observaciones}

Pruebas del paquete `org.signbb.backend.observaciones`, el que **valida, audita
y reenvía** la respuesta del cuidador hacia el motor de navegación. Son
**JUnit 5 + Mockito**, sin Spring, base de datos ni Redis.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `servicios` | `ServicioCapturaImplTest` | 4 | valida la selección contra la pregunta actual, audita la observación y reenvía al servicio de navegación |
| `modelo` | `SeleccionCuidadorTest` | 6 | validación del constructor de `SeleccionCuidador` según el tipo de interacción |

En total son **10 pruebas**.

## Qué se prueba y por qué importa

### Captura de la respuesta (`ServicioCapturaImplTest`)
Con `ServicioNavegacion` y `RepositorioObservaciones` **simulados con Mockito**:

- una sola opción se valida, se **audita** (`ObservacionEntity` guardada) y se
  reenvía al motor, devolviendo su resultado;
- una **selección múltiple** se reenvía completa: la captura **nunca decide**
  cuál opción domina (eso es del motor);
- una opción que **no pertenece** a la pregunta actual se rechaza **antes** de
  tocar el motor y **sin** guardar observación;
- si el recorrido ya está `FINALIZADO`, no se responde ni se audita.

### Validación de la selección (`SeleccionCuidadorTest`)
`SeleccionCuidador` es el value object que normaliza lo que marcó el cuidador
según el `TipoInteraccion`:

- `SI_NO` y `SELECCION_UNICA` exigen una sola opción;
- `SELECCION_MULTIPLE` acepta varias;
- se rechazan la lista vacía y el `recorridoId` vacío.

## Cómo ejecutarlas

    ./mvnw test -Dtest='org.signbb.backend.observaciones.**'

No requieren PostgreSQL ni Redis.

> La **auditoría** de observaciones vive en este módulo (`ServicioCaptura`); no
> existe todavía un paquete `org.signbb.backend.auditoria` implementado (solo
> `package-info.java`). La autoría de reglas, con su CRUD y publicación, está en
> \ref pruebas_autoria.
