# Pruebas del módulo de autoría {#pruebas_autoria}

> Índice general de la suite: \ref pruebas.

Pruebas del paquete `org.signbb.backend.autoria`, el que permite que un médico
arme un subgrafo clínico en el lienzo, lo edite, defina sus reglas y lo envíe a
revisión; y que un supervisor lo apruebe o lo rechace.

Las unitarias son JUnit 5 y Mockito, sin Spring, base de datos ni Redis. La de
integración (`AutoriaIntegracionTest`) levanta la aplicación completa con
PostgreSQL y Redis, como el CI.

## Enfoque

Desde el 31-oct-2026 la suite se enfoca en el **comportamiento esperado de los
CU02, CU06, CU07, CU08 y CU12** y en las excepciones que el SRS documenta. Se
retiraron los bordes técnicos que no cambian el comportamiento observable:
limpieza de imágenes en Silo, transiciones imposibles, colisiones de ids,
variantes de 404 y detalles de caché.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `servicios` | `EditorLienzoTest` | 14 | primera pregunta, "+", enlazar, editar, eliminar, validaciones e imágenes |
| `servicios` | `ValidadorLienzoTest` | 6 | avisos bloqueantes del borrador |
| `servicios` | `CompiladorSubgrafoTest` | 6 | lienzo → `GrafoClinicoJson`: pesos, tipos, aristas y salida válida |
| `servicios` | `PublicadorSubgrafoTest` | 12 | publicar, enlazar el puente, recomponer, despublicar y versionar |
| `servicios` | `ServicioAutoriaImplTest` | 19 | categorías, borradores, autor, estados, publicar, descartar y despublicar |
| `servicios` | `ServicioRevisionImplTest` | 10 | enviar, retirar, aprobar, rechazar y pendientes |
| `servicios` | `GuardiaAutoriaTest` | 8 | autor, otro médico, supervisor y revisión |
| `servicios` | `ServicioAutoriaReglasImplTest` | 7 | CRUD de la base de reglas y estados |
| `servicios` | `PublicadorReglaTest` | 5 | publicar/reemplazar la base de reglas |
| `servicios` | `CompiladorReglasTest` | 3 | borrador de reglas → `BaseReglasJson` (FND, niveles) |
| `servicios` | `ValidadorReglasTest` | 5 | avisos del borrador de reglas |
| `servicios` | `ResolutorCatalogoNodosTest` | 2 | catálogo de nodos con desenlace en terminales |
| `controladores` | `ControladorAutoriaTest` | 5 | códigos HTTP representativos de la API |
| `controladores` | `ControladorRevisionTest` | 3 | códigos HTTP de enviar, aprobar y rechazar |
| `modelo` | `NodoLienzoTest` | 2 | JSON del nodo y compatibilidad con lienzos sin imágenes |

En total son **107 pruebas unitarias**. Las 2 de integración
(`AutoriaIntegracionTest`) se cuentan aparte y están deshabilitadas con
`@Disabled` mientras se revisa la suite unitaria.

## Fixture

`servicios/LienzoFiebre` arma, con las mismas operaciones del editor, el
lienzo de fiebre del deck:

    ¿Cuánto marca el termómetro?
    ├─ Menos de 38 °C (LEVE) ──────┐
    ├─ Entre 38 y 39 °C (MODERADA) ┴→ ¿Está decaído o no quiere comer?
    │                                  ├─ Sí (MODERADA) → Consultar al médico
    │                                  └─ No (LEVE) → Recomendación
    └─ Más de 39 °C (GRAVE) → Lleve al bebé a urgencias ahora.

## Qué se prueba y por qué importa

### CU02: crear grafo (`EditorLienzoTest`, `ValidadorLienzoTest`, `CompiladorSubgrafoTest`)
- La primera pregunta es el inicio y sus respuestas quedan como "+".
- El "+" crea el nodo y conecta la respuesta; también se puede enlazar a una
  pregunta existente.
- Se rechazan una segunda primera pregunta, empezar con una conclusión, una
  conclusión sin desenlace y un círculo.
- Los avisos bloqueantes impiden publicar: sin primera pregunta, respuesta sin
  camino, una sola respuesta, conclusión incompleta y nodo suelto.
- El compilador conserva datos, pesos, tipo de interacción, terminales y
  aristas, y la salida pasa por el mapper estricto.

### CU07: editar grafo (`EditorLienzoTest`, `ServicioAutoriaImplTest`)
- Editar conserva los destinos de las respuestas que ya tenían id y crea las
  nuevas.
- Eliminar un nodo devuelve a "+" las respuestas que llegaban y poda la rama
  que quedó sin camino.
- Editar un borrador publicado lo deja en `CAMBIOS_SIN_PUBLICAR`.
- Descartar cambios devuelve el borrador a su última publicación.
- Un borrador publicado no se puede borrar.

### CU06: definir reglas (`EditorLienzoTest`, `CompiladorReglasTest`, `ValidadorReglasTest`, `ServicioAutoriaReglasImplTest`, `PublicadorReglaTest`)
- La base de reglas se crea para un grafo válido y el autor sale de la cuenta.
- Se agregan reglas con ids `R_N` y se conserva la FND (casos y condiciones).
- Faltar nombre, condiciones, destino o un nivel de alerta válido bloquea la
  publicación.
- Publicar reemplaza la base del grafo, recompone el contenido y registra una
  versión.

### CU08/CU12: publicar y revisar (`PublicadorSubgrafoTest`, `ServicioRevisionImplTest`, `GuardiaAutoriaTest`, controladores)
- Publicar guarda el subgrafo, agrega la opción en la pregunta puente y
  publica también su base de reglas.
- Republicar no duplica la referencia ni la opción.
- Con avisos bloqueantes no se guarda nada; si la composición falla, tampoco.
- Despublicar retira la fila, la referencia y la opción; guarda una versión
  nueva y permite volver a publicar.
- Enviar a revisión valida los avisos y deja el borrador en revisión; retirar
  lo devuelve a borrador o a cambios sin publicar.
- Aprobar publica y guarda el revisor; nadie aprueba lo suyo.
- Rechazar exige comentario y devuelve el borrador con él.
- Las pendientes excluyen las del propio supervisor.
- Solo el autor edita su subgrafo; el supervisor puede leer y revisar.

### API (`ControladorAutoriaTest`, `ControladorRevisionTest`)
- Crear responde 201, borrar 204 y una edición inválida 422 con
  `{mensaje, avisos: []}`.
- Editar lo de otro da 403; una edición concurrente da 409.
- Enviar con avisos da 422; aprobar y rechazar responden 200.

## Qué se quitó en la reorganización

| Archivo | Motivo |
|---|---|
| `ServicioAutoriaReglasAbstractoTest` (11) | Repetía el CRUD de `ServicioAutoriaReglasImplTest` con mocks. |
| `PoliticaAccesoBorradoresTest` (4) | La misma política se ejercita en `GuardiaAutoriaTest`. |
| Bordes técnicos del 31-oct-2026 | Limpieza de imágenes en Silo, descartes sin cambios, despublicar casos límite, colisiones de `grafoId`, bases raíz, doble envío y variantes de 404. |

## Cómo ejecutarlas

Desde `backend/`:

    ./mvnw test -Dtest='org.signbb.backend.autoria.**'   # unitarias
    ./mvnw verify                                        # suite completa, como el CI

## Resultados

Tras el recorte del 31-oct-2026, el módulo tiene **107 pruebas unitarias** y la
suite completa del backend **364** (2 omitidas por `AutoriaIntegracionTest`).
