# Pruebas del módulo de autoría {#pruebas_autoria}

> Índice general de la suite: \ref pruebas.

Pruebas del paquete `org.signbb.backend.autoria`, el que permite que un médico
arme un subgrafo clínico en el lienzo (un "+" en cada respuesta), vea los
avisos y lo publique para que quede navegable en la app de la cuidadora; y
definir la **base de reglas** de un grafo (CRUD del borrador y publicación).

Las unitarias son JUnit 5 y Mockito, sin Spring, base de datos ni Redis. La
de integración (`AutoriaIntegracionTest`) levanta la aplicación completa con
PostgreSQL y Redis, como el CI.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `servicios` | `EditorLienzoTest` | 25 | el "+", enlazar a una pregunta existente, editar y eliminar nodos; lo que el editor rechaza |
| `servicios` | `ValidadorLienzoTest` | 10 | un caso por cada aviso; nodos sueltos y choque de gravedades |
| `servicios` | `CompiladorSubgrafoTest` | 8 | lienzo → `GrafoClinicoJson`: pesos, tipos, aristas y metadata |
| `servicios` | `PublicadorSubgrafoTest` | 24 | upsert del subgrafo y de la opción en la raíz; categoría nueva visible; invalidar el contenido; rechazos; despublicar |
| `servicios` | `ServicioAutoriaImplTest` | 38 | categorías, ids de grafo, datos del subgrafo (nombre para la familia obligatorio), autor tomado de la cuenta, "Mis subgrafos", estados, descartar cambios, despublicar y errores |
| `servicios` | `PoliticaAccesoBorradoresTest` | 4 | quién lee y quién edita un borrador: autor, otro médico, supervisor y borrador sin autor |
| `servicios` | `GuardiaAutoriaTest` | 13 | la política aplicada a subgrafos y bases (403/404), quién crea una base y el bloqueo mientras está en revisión |
| `servicios` | `ServicioRevisionImplTest` | 13 | enviar, retirar, aprobar (publica) y rechazar con comentario; nadie aprueba lo suyo |
| `controladores` | `ControladorAutoriaTest` | 8 | códigos HTTP representativos de la API `/api/autoria`: 201, 403, 404, 409, 422 y 204 |
| `controladores` | `ControladorRevisionTest` | 4 | códigos HTTP de enviar, aprobar y rechazar |
| `modelo` | `NodoLienzoTest` | 2 | el JSON del nodo: las imágenes se guardan y se leen, y un nodo guardado sin imágenes se lee con la lista vacía |
| `servicios` | `CompiladorReglasTest` | 3 | borrador de reglas → `BaseReglasJson` (FND, niveles) |
| `servicios` | `ValidadorReglasTest` | 6 | un aviso por cada problema del borrador de reglas |
| `servicios` | `ResolutorCatalogoNodosTest` | 2 | el catálogo de nodos (de un lienzo y del grafo raíz) pone el desenlace en los terminales |
| `servicios` | `PublicadorReglaTest` | 7 | publica/reemplaza la base de reglas de un grafo, recompone el contenido y registra una versión |
| `servicios` | `ServicioAutoriaReglasImplTest` | 12 | CRUD de la base de reglas, autor tomado de la cuenta, "Mis bases", publicación y descartar cambios |
| (raíz) | `AutoriaIntegracionTest` | 2 | de punta a punta: armar, aprobar (publicar) y recorrer como cuidadora; **deshabilitada temporalmente** |

En total son **179 pruebas unitarias**. Las 2 de integración
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

"Menos de 38 °C" llega a una pregunta que ya existía (la opción "Pregunta
existente" del panel), así que esa pregunta recibe dos respuestas de distinta
gravedad. El borrador de prueba es `grafo_fiebre`, de la categoría
neurológica (`puente_neuro`).

## Qué se prueba y por qué importa

### Edición (`EditorLienzoTest`)
- **El "+".** Crea el nodo y conecta la respuesta. Sin respuesta crea la
  primera pregunta; una segunda primera pregunta, o empezar con una
  conclusión, se rechaza. Una respuesta que ya tiene camino no acepta otro.
- **Pregunta existente.** Solo se puede ir a una pregunta, no a la misma, y
  nunca a una que ya lleva de vuelta al origen: **no se pueden crear
  círculos**. Quitar el enlace deja la respuesta como "+" otra vez.
- **Editar.** Las respuestas que vienen con id conservan su destino, las
  nuevas nacen como "+" y el tipo del nodo no cambia.
- **Eliminar.** Las respuestas que llegaban vuelven a ser "+" y se poda solo
  la rama que quedó sin camino; lo que ya estaba suelto se conserva.
- Los ids los genera el servidor (`pregunta_N`, `conclusion_N`, `r_N`) y no
  se reutilizan.

### Avisos (`ValidadorLienzoTest`)
- **Bloquean la publicación:** sin primera pregunta, texto vacío, menos de 2
  respuestas, respuesta repetida, respuesta sin camino, conclusión sin
  desenlace, círculo y nodo suelto.
- Los mensajes están en lenguaje humano, por ejemplo: «La respuesta "Más de
  39 °C" de "¿Cuánto marca el termómetro?" todavía no dice qué pasa
  después.».
- Un nodo suelto no se revisa por dentro: basta con conectarlo o eliminarlo.
- **No se avisa** el choque de gravedades: se resuelve al compilar con la
  mayor, como se acordó.

### Compilar (`CompiladorSubgrafoTest`)
- El `peso` de un nodo es la **mayor gravedad** de las respuestas que llegan
  a él; la primera pregunta lleva 1.
- "¿Puede marcar varias?" da `SELECCION_MULTIPLE`; si no, `SELECCION_UNICA`.
- Las conclusiones quedan como `terminal` con su desenlace y el mensaje como
  `label`.
- Los nodos sueltos bloquean la publicación; si se compila un documento que
  no pasó por la validación, se omiten.
- La salida pasa por `GrafoClinicoMapper.desde`, la misma validación estricta
  que el contenido del sistema.

### Publicar (`PublicadorSubgrafoTest`)
- Guarda el subgrafo y agrega, en la pregunta puente del grafo raíz, la
  opción del subgrafo y su nodo `ref_<grafoId>`. **Republicar no duplica**
  ni la opción ni el nodo.
- Publicar en una categoría que todavía no tenía subgrafos (piel o
  temperatura) la hace aparecer en la pregunta inicial de la cuidadora.
- Después del commit invalida el contenido en Redis y recarga el que está en
  memoria (`ContextoClinico.recargar()`), en ese orden.
- **No guarda nada** si hay avisos bloqueantes, si la opción ya existe en la
  pregunta puente (por ejemplo "Iniciar"), si la pregunta puente no está o si
  el contenido compuesto no pasa la validación.

### Despublicar (`PublicadorSubgrafoTest`)
- `retirar` quita la fila del subgrafo, su nodo `ref_<grafoId>` y las aristas
  que lo tocan; la pregunta puente vuelve a tener solo sus opciones de antes
  y los demás subgrafos no cambian.
- Guarda una versión nueva del contenido (origen "Despublicación de ...") sin
  el subgrafo, e invalida Redis y recarga el contexto después de guardar.
- Si era el único subgrafo publicado, la raíz queda solo con la pregunta
  inicial sin opciones y la consulta no disponible; la operación no se bloquea.
- Un subgrafo despublicado se puede volver a publicar sin duplicar la opción.

### Servicio (`ServicioAutoriaImplTest`)
- Las categorías salen de las preguntas puente del grafo raíz: piel,
  temperatura, respiración, gastrointestinal y neurológico. Piel y
  temperatura se ofrecen aunque todavía no tengan subgrafos.
- El `grafoId` se arma con el nombre, sin tildes ni signos
  (`grafo_fiebre_en_ninos_pequenos`) y con sufijo si ya existe
  (`grafo_respiracion_3`).
- Editar un subgrafo publicado lo deja en **CAMBIOS_SIN_PUBLICAR**; publicar
  lo vuelve a **PUBLICADO**.
- El nombre para la familia (`etiquetaOpcion`) es obligatorio: sin él, crear
  o editar responde 422.
- Un borrador publicado no se puede borrar; un id inexistente da 404.
- Despublicar devuelve el borrador a **BORRADOR** (sin `publicadoEn` ni copia
  publicada, con el lienzo y los datos intactos); se rechaza si nunca se
  publicó, y si el contenido restante no es válido el borrador sigue
  **PUBLICADO**. Ya despublicado se puede republicar o borrar.

### Reglas: compilar, validar y publicar (`CompiladorReglasTest`, `ValidadorReglasTest`, `PublicadorReglaTest`)
- La base de reglas del borrador se compila a `BaseReglasJson` conservando la
  **FND** (casos y condiciones), el nivel y el `nodoDestino`.
- `ValidadorReglas` produce un aviso por cada problema del borrador (sin
  nombre, sin destino, con nodos inexistentes, etc.).
- Al publicar se **reemplaza** la base del grafo en el conjunto de bases, se
  recompone el contenido (grafo + reglas) y, tras el commit, se invalida el
  cache y se recarga el contexto. No se guarda nada si la composición no es
  válida.

### Reglas: CRUD y publicación (`ServicioAutoriaReglasImplTest`)
- **CRUD del borrador:** crear la base de un grafo, agregar/editar/eliminar
  reglas (ids `R_N` no reutilizados) y listar.
- **Estados:** editar una base publicada la deja en `CAMBIOS_SIN_PUBLICAR`;
  una base publicada no se puede borrar.
- **Publicación:** exige que el lienzo del subgrafo esté publicado (sin
  `CAMBIOS_SIN_PUBLICAR`) y delega en `PublicadorRegla`.

### Autor y acceso (`PoliticaAccesoBorradoresTest`, `GuardiaAutoriaTest`)
Cada subgrafo y cada base de reglas guarda el `autorId` de quien lo creó; el
nombre y la institución salen de la cuenta y se ignora lo que mande el
request. La política es:

- **Leer:** el autor o un supervisor. Otro médico recibe 403 y un id
  inexistente, 404.
- **Editar** (datos, nodos, reglas, descartar, eliminar): solo el autor. Un
  supervisor tampoco edita lo de otro.
- **Borradores sin autor** (datos de antes de las cuentas): solo los lee un
  supervisor.
- **Crear una base de reglas:** solo el autor del subgrafo de ese `grafoId`;
  la del grafo raíz (o de un grafo del sistema), solo un supervisor.
- **En revisión** nadie edita el subgrafo ni su base (422); el autor sí puede
  retirarlo y el supervisor verlo.
- "Mis subgrafos" y "Mis bases" (`listar`) traen solo lo del médico; el
  supervisor ve todo.

### Revisión del supervisor (`ServicioRevisionImplTest`, `ControladorRevisionTest`)
Ya no se publica directo: el autor **envía a revisión** y un supervisor que no
sea el autor **aprueba** (eso publica el subgrafo y sus reglas) o **rechaza**
con un comentario.

- Enviar con avisos bloqueantes da 422 con la lista y no cambia nada; enviar
  dos veces, o algo publicado sin cambios, también da 422. Un subgrafo
  publicado sí se envía si cambiaron sus reglas.
- Enviar borra el comentario del rechazo anterior.
- Retirar (o rechazar) lo devuelve a `BORRADOR` si nunca se publicó, o a
  `CAMBIOS_SIN_PUBLICAR` si ya tenía publicación.
- Aprobar algo que no está en revisión da 422 y aprobar lo propio da **403**,
  aunque quien lo intente sea supervisor. Al aprobar se guardan el revisor y
  la fecha.
- Rechazar sin comentario da 422.
- Las revisiones pendientes excluyen las del propio supervisor.
- Los endpoints `publicar` ya no existen (404 o 405).

### API (`ControladorAutoriaTest`)
- Crear responde 201 y borrar 204.
- Una edición inválida responde **422 con `{mensaje, avisos: []}`**.
- Quien no es el autor recibe **403** al editar.
- Un id inexistente da 404 y una edición concurrente (`@Version`) da 409.
- Despublicar responde 200 con el borrador en `BORRADOR` y 422 con el mensaje
  si el contenido restante no es válido.

### Punta a punta (`AutoriaIntegracionTest`, deshabilitada temporalmente)
Arma con el servicio un subgrafo de fiebre (una pregunta y dos
conclusiones), lo aprueba (es decir, lo publica) y lo recorre con `ServicioNavegacion`:
`pregunta_inicial → neurologico → puente_neuro → opción nueva → pregunta
del subgrafo → "Más de 39 °C" → URGENCIA`. En `puente_neuro` siguen las
opciones del sistema y aparece la nueva. No es `@Transactional`, porque la
recarga del contenido ocurre después del commit; al terminar borra lo que
creó, restaura el grafo raíz e invalida el contenido.

Está **deshabilitada temporalmente** con `@Disabled` mientras se revisa la
suite unitaria por separado. Para volver a ejecutarla, quitar la anotación y
levantar PostgreSQL y Redis.

### Versiones del contenido (republicar sin romper recorridos en curso)
- `PublicadorSubgrafoTest`: cada publicación guarda una versión con el grafo
  compuesto (el subgrafo nuevo y el resto del contenido); republicar guarda
  otra; si no se puede publicar no se guarda ninguna.
- `cache/ResolvedorVersionesTest`: la vigente es la más reciente; sin
  versiones la primera es una foto del contenido actual; una clave de Redis
  por versión; si Redis no responde se lee de la base.
- `navegacion/modelo/ContextoClinicoTest`: cada versión tiene su propio
  grafo, se carga una sola vez y `recargar()` hace que lo nuevo use la
  versión nueva.
- `navegacion/servicios/ServicioNavegacionVersionesTest`: un recorrido en
  curso sigue con su versión aunque se publique otra; los nuevos usan la
  nueva; una sesión guardada antes de las versiones usa la vigente.

### Imágenes de las preguntas
- `EditorLienzoTest`: una pregunta admite hasta 3 imágenes; se rechazan más de
  3, repetidas y nombres que no dio el servidor; editar sin mandar `imagenes`
  conserva las actuales y mandar `[]` las quita; una conclusión no lleva.
- `CompiladorSubgrafoTest`: las imágenes salen en `metadata.archivos` y
  `metadata.src` de la pregunta.
- `NodoLienzoTest`: un lienzo guardado antes de las imágenes se sigue leyendo.
- `media/ServicioImagenesTest` y `media/ControladorImagenesTest`: formato
  (por la firma del archivo, no por el nombre), tope de 1 MB, 201 / 422 / 503
  al subir y 200 / 404 al ver. Usan un almacén en memoria: **no necesitan
  Silo**. Con Silo levantado se prueba a mano subiendo una imagen desde el
  lienzo.

## Cómo ejecutarlas

Desde `backend/`:

    ./mvnw test -Dtest='org.signbb.backend.autoria.**'   # unitarias (no necesitan PostgreSQL ni Redis)
    ./mvnw verify                                        # suite completa, como el CI (contenedores arriba)
