# Pruebas del módulo de autoría

Pruebas del paquete `org.signbb.backend.autoria`, el que permite que un médico
arme un subgrafo clínico en el lienzo (un "+" en cada respuesta), vea los
avisos y lo publique para que quede navegable en la app de la cuidadora. Esta
entrega cubre subgrafos **sin reglas**.

Las unitarias son JUnit 5 y Mockito, sin Spring, base de datos ni Redis. La
de integración (`AutoriaIntegracionTest`) levanta la aplicación completa con
PostgreSQL y Redis, como el CI.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `servicios` | `EditorLienzoTest` | 18 | el "+", enlazar a una pregunta existente, editar y eliminar nodos; lo que el editor rechaza |
| `servicios` | `ValidadorLienzoTest` | 10 | un caso por cada aviso; nodos sueltos y choque de gravedades |
| `servicios` | `CompiladorSubgrafoTest` | 7 | lienzo → `GrafoClinicoJson`: pesos, tipos, aristas y metadata |
| `servicios` | `PublicadorSubgrafoTest` | 8 | upsert del subgrafo y de la opción en la raíz; invalidar el contenido; rechazos |
| `servicios` | `ServicioAutoriaImplTest` | 10 | categorías, ids de grafo, datos del subgrafo, estados y errores |
| `controladores` | `ControladorAutoriaTest` | 7 | códigos HTTP y cuerpos de error de la API `/api/autoria` |
| (raíz) | `AutoriaIntegracionTest` | 1 | de punta a punta: armar, publicar y recorrer como cuidadora |

En total son **61 pruebas**.

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
  desenlace y círculo.
- Los mensajes están en lenguaje humano, por ejemplo: «La respuesta "Más de
  39 °C" de "¿Cuánto marca el termómetro?" todavía no dice qué pasa
  después.».
- **No bloquea:** un nodo suelto. Avisa que no se publicará.
- **No se avisa** el choque de gravedades: se resuelve al compilar con la
  mayor, como se acordó.

### Compilar (`CompiladorSubgrafoTest`)
- El `peso` de un nodo es la **mayor gravedad** de las respuestas que llegan
  a él; la primera pregunta lleva 1.
- "¿Puede marcar varias?" da `SELECCION_MULTIPLE`; si no, `SELECCION_UNICA`.
- Las conclusiones quedan como `terminal` con su desenlace y el mensaje como
  `label`.
- Los nodos sueltos no se publican.
- La salida pasa por `GrafoClinicoMapper.desde`, la misma validación estricta
  que el contenido del sistema.

### Publicar (`PublicadorSubgrafoTest`)
- Guarda el subgrafo y agrega, en la pregunta puente del grafo raíz, la
  opción del subgrafo y su nodo `ref_<grafoId>`. **Republicar no duplica**
  ni la opción ni el nodo.
- Después del commit invalida el contenido en Redis y recarga el que está en
  memoria (`ContextoClinico.recargar()`), en ese orden.
- **No guarda nada** si hay avisos bloqueantes, si la opción ya existe en la
  pregunta puente (por ejemplo "Iniciar"), si la pregunta puente no está o si
  el contenido compuesto no pasa la validación.

### Servicio (`ServicioAutoriaImplTest`)
- Las categorías salen de las preguntas puente del grafo raíz.
- El `grafoId` se arma con el nombre, sin tildes ni signos
  (`grafo_fiebre_en_ninos_pequenos`) y con sufijo si ya existe
  (`grafo_respiracion_3`).
- Editar un subgrafo publicado lo deja en **CAMBIOS_SIN_PUBLICAR**; publicar
  lo vuelve a **PUBLICADO**.
- Un borrador publicado no se puede borrar; un id inexistente da 404.

### API (`ControladorAutoriaTest`)
- Crear responde 201 y borrar 204.
- Una edición inválida responde **422 con `{mensaje, avisos: []}`** y publicar
  con avisos responde 422 con la lista.
- Un id inexistente da 404 y una edición concurrente (`@Version`) da 409.
- Quitar el destino de una respuesta acepta `{"nodoId": null}`.

### Punta a punta (`AutoriaIntegracionTest`)
Arma con el servicio un subgrafo de fiebre (una pregunta y dos
conclusiones), lo publica y lo recorre con `ServicioNavegacion`:
`pregunta_inicial → neurologico → puente_neuro → opción nueva → pregunta
del subgrafo → "Más de 39 °C" → URGENCIA`. En `puente_neuro` siguen las
opciones del sistema y aparece la nueva. No es `@Transactional`, porque la
recarga del contenido ocurre después del commit; al terminar borra lo que
creó, restaura el grafo raíz e invalida el contenido.

## Cómo ejecutarlas

Desde `backend/`:

    ./mvnw test -Dtest='org.signbb.backend.autoria.**'   # unitarias (no necesitan PostgreSQL ni Redis)
    ./mvnw verify                                        # suite completa, como el CI (contenedores arriba)
