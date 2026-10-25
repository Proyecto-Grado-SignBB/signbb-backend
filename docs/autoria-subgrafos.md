# SignBB · Autoría de subgrafos clínicos: qué se hizo y qué falta

> Resumen para el equipo · actualizado el 16-oct-2026

## 1. Qué es esta parte

El **médico** arma en un lienzo un *subgrafo clínico*, es decir, el camino de preguntas que lleva a una conclusión para un signo. Al **publicarlo**, ese subgrafo aparece como una opción nueva en la app de la **cuidadora**, dentro de la categoría que eligió el médico.

```
Cuidadora:  ¿Qué le pasa al bebé?  ──piel──▶  ¿Qué cambio observa en la piel o los ojos?
                                                 └─ «Piel u ojos amarillos»  ◀── lo agrega el médico al publicar
                                                       └─ preguntas del subgrafo … → Recomendación / Consulta / Urgencia
```

Cada subgrafo puede tener además su **base de reglas clínicas** (reglas de alarma en FND: OR de casos, cada caso un AND de condiciones). Las reglas se editan en su propia pantalla y **se publican junto con el subgrafo**.

## 2. Ramas y cómo están

| Repo | Rama | Sale de | Último commit | Estado |
|---|---|---|---|---|
| `signbb-backend` | `autoria` | `navegacion` | `bb93375` (14-oct) | CI verde. Ya incluye `autoria-reglas` |
| `signbb-frontend` | `autoria` | — | `252fad0` (14-oct) | Ya incluye `autoria-lienzo` y `autoria-reglas` |
| `signbb-backend` | `seguridad` | `autoria` (16-oct) | (16-oct) | CI verde. Cuentas, roles, autor, revisión del supervisor y recorrido del padre |
| `signbb-frontend` | `seguridad-front` | `autoria` (16-oct) | — | Ingreso y autor tomado de la sesión. Sin pantallas de supervisor |

- **La rama de trabajo es `autoria` en los dos repos.** `autoria-lienzo` y `autoria-reglas` ya están integradas ahí y no deben usarse para trabajo nuevo.
- **No se ha hecho merge** de `autoria` hacia `navegacion`, `develop` ni `main`.
- Las reglas usan el formato FND plano (`casos: Condicion[][]`). El Y/O anidado de `feature/motor-and-or` quedó descartado por ahora.

## 3. Pasos que seguimos

### Back (`signbb-backend`, rama `autoria`)
1. **Modelo y persistencia del borrador** (`c545730`): `BorradorSubgrafoEntity`, que guarda el lienzo como JSON (`DocumentoLienzo`, `NodoLienzo`, `RespuestaLienzo`). Lleva `@Version` para detectar ediciones simultáneas y los estados `BORRADOR`, `PUBLICADO` y `CAMBIOS_SIN_PUBLICAR`. Es independiente del grafo publicado.
2. **Edición del lienzo y avisos** (`25ec6e2`):
   - `EditorLienzo` maneja el "+", el enlace a una pregunta existente (sin permitir círculos) y editar o eliminar nodos (podando la rama huérfana).
   - `ValidadorLienzo` genera avisos en lenguaje humano. Todos bloquean la publicación; un nodo suelto impide publicar hasta conectarlo a una respuesta o eliminarlo.
3. **Compilar y publicar** (`3733bad`):
   - `CompiladorSubgrafo` convierte el lienzo en un `GrafoClinicoJson`. El peso es la mayor gravedad que llega al nodo, y el tipo de selección (única o múltiple) se respeta.
   - `PublicadorSubgrafo` guarda el subgrafo y agrega su opción y el nodo `ref_<grafoId>` en la pregunta puente del grafo raíz. Republicar no duplica nada.
   - **Publicar el subgrafo publica también su base de reglas**: la acción es única y atómica (si algo falla, no se publica ni el grafo ni las reglas). Las reglas no se publican por su cuenta desde la interfaz; si cambian las reglas, se republica el subgrafo. El supervisor que es autor puede publicarlo directamente; los demás médicos, al aprobar su revisión. Para el grafo raíz (que no tiene subgrafo) la base no se publica desde aquí. La publicación registra una sola versión, al final, con el grafo y las reglas ya publicados.
   - **Validación cruzada**: si el médico borra un nodo que una regla usa como condición o destino, `ValidadorLienzoReglas` lo detecta, muestra un aviso bloqueante que enlaza a las reglas y evita publicar.
   - Al publicar se invalida Redis y se recarga el `ContextoClinico`.
4. **API `/api/autoria`** (`703ecc4`). Ver la tabla de la sección 4.
   - Los errores salen con el formato `{mensaje, avisos}`: 422 si la edición es inválida o hay avisos, 404 si no existe y 409 si hubo una edición concurrente.
5. **Pruebas** (`4f6539d`, `d32b685`): son 63 del módulo, incluida una de punta a punta. Esa prueba arma un subgrafo, lo publica y lo recorre como cuidadora hasta URGENCIA. El detalle está en `backend/src/test/java/org/signbb/backend/autoria/README.md`.
6. **Categorías piel y temperatura** (`5b2094a`):
   - Las categorías **son las preguntas puente del grafo raíz** (`grafo_raiz.json`). Ahora son piel, temperatura, respiración, gastrointestinal y neurológico.
   - Una categoría sin subgrafos no se le muestra a la cuidadora, porque el compositor la poda, y aparece sola cuando alguien publica en ella.
7. **Nombre para la familia obligatorio** (`c23c5e7`): `etiquetaOpcion` es el único texto que ve la cuidadora. Si viene vacío, el back responde 422 con un mensaje claro. Ya no se usa el nombre técnico como respaldo.
8. **Limpieza de versiones e imágenes** (`mantenimiento/`): `LimpiezaProgramada` corre todos los días a las 3:30 (primero versiones, luego imágenes) y no tiene endpoint.
   - `PoliticaRetencionVersiones` decide: se quedan las 20 más recientes y las reemplazadas hace menos de 25 h (el TTL de las sesiones es 24 h, así que nadie sigue en una versión más vieja). `LimpiadorVersiones` las borra de la base, de Redis (`ResolvedorVersiones.olvidar`) y de la memoria (`ContextoClinico.olvidar`).
   - `LimpiadorImagenes` lista Silo (`AlmacenImagenes.listar`) y borra (`eliminar`) lo que no aparece en ningún borrador, copia publicada, grafo ni versión conservada. Respeta 24 h de gracia porque la imagen se sube antes de guardar la pregunta.
9. **Reglas clínicas** (Simón, `3a14cda`, `e29af28`, `f951fda`, `0027a31`, `bbdc0da`, `4948827`, `8783f82`):
   - API `/api/autoria/bases` para crear, editar, borrar y descartar bases de reglas y sus reglas. Incluye avisos y un catálogo de nodos que muestra el desenlace de cada conclusión.
   - Las reglas se guardan en versiones junto al grafo (`VersionContenidoEntity`). Una versión sin reglas queda con la base vacía.
   - Publicar el subgrafo publica sus reglas en una sola acción, y despublicarlo las retira.
   - `ValidadorLienzoReglas` bloquea la publicación si una regla usa un nodo que ya no existe.
10. **Documentación de la API** (Simón, `7bbe192`): springdoc y `docs/api/openapi.yaml`.
11. **Pruebas** (Simón, `45eb370`, `2423a0d`): pruebas unitarias del motor de reglas y de la autoría de reglas, el plan de pruebas (`Plan_pruebas_navegacion_SignBB.xlsx`) y `pruebas_manuales/pruebas_versiones.md`.

### Front (`signbb-frontend`, rama `autoria`, proyecto `ui-autoria`)
1. **Cliente de la API nueva** (`c383d0d`): `core/http/autoria.ts` y `models/lienzo.model.ts`. Reemplazan `autoria.model.ts` y la API vieja `/grafos`.
2. **Tablero y lienzo con "+"** (`18a6e54`):
   - `features/subgrafos/tablero` lista los subgrafos y crea uno nuevo con su nombre, categoría y nombre para la familia.
   - `features/subgrafos/lienzo` dibuja el árbol, que crece hacia la derecha y tiene un "+" en cada respuesta sin camino. La distribución está en `layout.ts`.
   - `panel-nodo` sirve para crear o editar preguntas y conclusiones, y para enlazar a una pregunta existente.
   - **Se eliminaron las pantallas viejas de `features/grafos`** (flujo, editores de pregunta y desenlace, reglas y tablero) porque usaban la API anterior.
3. **Avisos y publicar** (`4cd67a7`): una barra de avisos que al tocarse lleva al nodo, y el modal de publicar con los avisos bloqueantes.
4. **Diálogos y errores propios** (`9dac070`):
   - Al eliminar ya no aparece el `confirm()` del navegador. Lo reemplaza `core/ui/dialogo-confirmar`, que muestra el error dentro del diálogo.
   - Hay mensajes para 0 (sin conexión), 400, 404, 409, 422 y 5xx, y una confirmación breve cuando algo sale bien.
5. **Nombre para la familia obligatorio** (`fa3b46e`):
   - El campo explica al médico que escriba sin términos médicos, por ejemplo «Piel u ojos amarillos» en lugar de «Ictericia».
   - En el lienzo, **«Editar datos»** (`core/ui/modal-datos`) permite corregir el nombre técnico y el de la familia.
6. **Mejoras de Daniela** (`ec3ddd2`, `df38734`, `aea0470`, `7f21248`, `2484d81`): imágenes en las preguntas, mejoras de las pantallas de subgrafo y de resultado, y el botón para despublicar.
7. **Editor de reglas de alarma** (Simón, `da16b35`, `327bae8`, `80b9cd2`, `9e99401`, `2e2010c`, `b1d311d`, `252fad0`):
   - `features/reglas`: un tablero de bases de reglas, el editor y un visor que dibuja la regla como grafo, con escala, centrado y expandir.
   - Se pueden descartar los cambios de una base de reglas.
   - El modal de publicar muestra las reglas que salen junto con el subgrafo, y el lienzo tiene el enlace «Ir a Reglas».
8. **Documentación del front con Compodoc** (Simón, `e04c0f6`): `npm run docs` genera la documentación de las tres apps en `docs/`.

## 4. API (`/api/autoria`)

En la rama `seguridad` todo `/api/autoria` pide token de `MEDICO` o
`MEDICO_SUPERVISOR` (ver el README raíz). El autor y la institución salen de la
cuenta: si el request los trae, se ignoran. Cada médico ve y edita solo lo suyo;
un supervisor ve todo, pero solo edita y publica directamente lo propio.

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/categorias` | Preguntas puente del grafo raíz |
| GET / POST | `/subgrafos` | Listar los propios (el supervisor, todos) / crear (nombre, puenteId, etiquetaOpcion) |
| GET / PUT / DELETE | `/subgrafos/{id}` | Ver / editar datos / borrar (solo si nunca se publicó) |
| POST | `/subgrafos/{id}/nodos` | Crear nodo (el "+") |
| PUT / DELETE | `/subgrafos/{id}/nodos/{nodoId}` | Editar / eliminar nodo |
| PUT | `/subgrafos/{id}/respuestas/{respuestaId}/destino` | Enlazar a una pregunta existente (`{"nodoId": null}` lo quita) |
| GET | `/subgrafos/{id}/avisos` | Avisos actuales |
| POST | `/subgrafos/{id}/enviar-revision` | El autor lo envía a revisión (422 con avisos del lienzo y de las reglas si hay bloqueantes). Mientras está en revisión no se edita el subgrafo ni su base |
| POST | `/subgrafos/{id}/retirar-revision` | El autor lo retira de revisión para volver a editarlo |
| GET | `/revisiones` | **Supervisor:** subgrafos en revisión de otros autores |
| POST | `/subgrafos/{id}/aprobar` | **Supervisor que no sea el autor:** publica el subgrafo **y su base de reglas** |
| POST | `/subgrafos/{id}/publicar` | **Supervisor autor:** publica directamente el subgrafo **y su base de reglas**, sin pasar por revisión. Se permite desde `BORRADOR` o `CAMBIOS_SIN_PUBLICAR`, y si solo cambiaron las reglas de uno ya publicado |
| POST | `/subgrafos/{id}/rechazar` | **Supervisor que no sea el autor:** lo devuelve con `{"comentario"}` (obligatorio) |
| POST | `/subgrafos/{id}/descartar` | Descartar los cambios sin publicar y volver a la última publicación |
| POST | `/subgrafos/{id}/despublicar` | **Supervisor.** Despublicar: quita el subgrafo y su base de reglas; los borradores (lienzo y reglas) vuelven a `BORRADOR` con su documento intacto (422 si no está publicado o si es el único subgrafo publicado) |

Reglas (`/api/autoria/bases`):

| Método | Ruta | Para qué |
|---|---|---|
| GET / POST | `/bases` | Listar / crear bases de reglas |
| GET | `/bases/catalogo` | Nodos disponibles para las condiciones, con el desenlace de cada conclusión |
| GET / PUT / DELETE | `/bases/{id}` | Ver / editar / borrar una base |
| POST | `/bases/{id}/reglas` | Agregar una regla |
| PUT / DELETE | `/bases/{id}/reglas/{reglaId}` | Editar / eliminar una regla |
| GET | `/bases/{id}/avisos` | Avisos de la base |
| POST | `/bases/{id}/descartar` | Descartar los cambios sin publicar |

`POST /subgrafos/{id}/publicar` es exclusivo del supervisor autor: publica
directamente el subgrafo y su base de reglas, sin revisión. La base de reglas no
tiene un `POST /bases/{id}/publicar` propio: se publica con el subgrafo. El
resto de los médicos publica al aprobar su revisión; si cambian solo las reglas
de un subgrafo publicado, el supervisor puede republicarlo directamente y los
demás lo envían a revisión.

La especificación completa está en `docs/api/openapi.yaml`.

## 5. Cómo probarlo en local

**Requisitos:** Java 25, Docker Desktop (encendido), Node.js con npm y Git. Maven no hace falta, porque se usa el wrapper (`mvnw` / `mvnw.cmd`).

**Puertos:**

| Qué | Dónde |
|---|---|
| Back (API) | `http://localhost:8080/api` |
| Autoría (médico) | `http://localhost:4300` |
| Consulta (cuidadora) | `http://localhost:4200` |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |

Las rutas son relativas a la raíz de cada repo.

### Mac (Terminal, zsh)

**Back:**
```bash
cd signbb-backend
git fetch origin && git checkout autoria && git pull

cd contenedores
docker compose down -v      # solo si ya tenías la base: borra los datos y vuelve a sembrar el grafo raíz
docker compose up -d

cd ../backend
./mvnw spring-boot:run      # deja esta terminal abierta
```

**Front.** Abre otra terminal:
```bash
cd signbb-frontend
git fetch origin && git checkout autoria && git pull
npm install                 # hace falta otra vez: se agregó @compodoc/compodoc
npx ng build ui-compartida  # librería compartida; hay que compilarla antes que las apps
npx ng serve ui-autoria     # http://localhost:4300
```

**App de la cuidadora.** Abre una tercera terminal:
```bash
cd signbb-frontend
npx ng serve ui-consulta    # http://localhost:4200
```

**Pruebas:**
```bash
# Back. Necesita los contenedores arriba
cd signbb-backend/backend && ./mvnw verify

# Front
cd signbb-frontend
CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  npx ng test ui-autoria --watch=false --browsers=ChromeHeadless
```

### Windows (PowerShell)

**Back:**
```powershell
cd signbb-backend
git fetch origin; git checkout autoria; git pull

cd contenedores
docker compose down -v      # solo si ya tenías la base: borra los datos y vuelve a sembrar el grafo raíz
docker compose up -d

cd ..\backend
.\mvnw.cmd spring-boot:run  # deja esta ventana abierta
```

**Front.** Abre otra ventana de PowerShell:
```powershell
cd signbb-frontend
git fetch origin; git checkout autoria; git pull
npm install
npx ng build ui-compartida
npx ng serve ui-autoria     # http://localhost:4300
```

**App de la cuidadora.** Abre una tercera ventana:
```powershell
cd signbb-frontend
npx ng serve ui-consulta    # http://localhost:4200
```

**Pruebas:**
```powershell
# Back. Necesita los contenedores arriba
cd signbb-backend\backend; .\mvnw.cmd verify

# Front. Si Karma no encuentra Chrome, define CHROME_BIN
cd signbb-frontend
$env:CHROME_BIN = "C:\Program Files\Google\Chrome\Application\chrome.exe"
npx ng test ui-autoria --watch=false --browsers=ChromeHeadless
```

**Problemas comunes en Windows:**
- **PowerShell bloquea `npx`** con el error «la ejecución de scripts está deshabilitada». Ejecuta una vez `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.
- **Necesitas `curl`.** Usa `curl.exe`; en PowerShell, `curl` es un alias de `Invoke-WebRequest`.
- **Errores de `JAVA_HOME`.** Verifica que `java -version` muestre la 25.

### Verificar que el back responde (Mac y Windows)

```bash
curl http://localhost:8080/api/autoria/categorias     # en Windows: curl.exe ...
```

Debe listar piel, temperatura, respiración, gastrointestinal y neurológico. **Si salen las categorías viejas**, falta el `docker compose down -v`. `DatabaseInit` solo siembra si no existe un grafo raíz.

### Prueba sugerida: «Ictericia en la piel»

1. En `http://localhost:4300`, crea un subgrafo con la categoría **Piel** y el nombre para la familia «Piel u ojos amarillos».
2. Arma una pregunta con sus respuestas y conclusiones usando los "+".
3. Publica.
4. En `http://localhost:4200` (cuidadora), entra a Piel y verifica que aparece «Piel u ojos amarillos» y que se recorre hasta la conclusión.

### Probar la limpieza a mano

La tarea corre sola a las 3:30. Para probarla sin esperar, cambia temporalmente en `backend/src/main/resources/application.properties` la línea `signbb.limpieza.cron=0 * * * * *` (corre cada minuto) y reinicia el back.

1. **Imágenes:** sube una imagen en una pregunta y no guardes la pregunta. En el log debe salir `Limpieza de imágenes: ... se borraron 0`: la imagen tiene menos de 24 h. Para ver el borrado de verdad, sube a Silo (consola en `localhost:9001`) un archivo con nombre de la forma `00000000-0000-0000-0000-000000000001.png` y deja pasar más de 24 h, o cambia la gracia (`LimpiadorImagenes.GRACIA`) a 1 minuto solo para la prueba. Debe desaparecer solo si ningún lienzo la usa.
2. **Imagen en uso:** una imagen puesta en una pregunta guardada nunca debe borrarse, ni siquiera con la gracia en cero.
3. **Versiones:** publica y despublica más de 20 veces (o inserta filas en `version_contenido`) y mira el log: `Limpieza de versiones: se borraron N de M`. La versión vigente y las 20 más recientes deben seguir en la tabla.
4. Deja el cron y la gracia como estaban antes de commitear.

### Errores a verificar

- Crear sin nombre para la familia no debe dejar.
- Eliminar debe mostrar el diálogo de la app, no el del navegador.
- Con el back apagado (Ctrl+C en su terminal), el error debe salir dentro del diálogo.

## 6. Pendientes para tener completa la parte de subgrafos clínicos

### Imprescindibles
- [x] **Reglas clínicas en los subgrafos.** Hecho por Simón: editor de reglas en FND plano, versionadas junto al grafo y publicadas con el subgrafo.
- [x] **Módulo de seguridad** (back en la rama `seguridad`, front en `seguridad-front`; sin merge todavía):
  - login con JWT y roles `PADRE`, `MEDICO` y `MEDICO_SUPERVISOR`; toda la API pide token y CORS solo acepta los orígenes del front;
  - el autor y la institución salen de la cuenta, y «Mis subgrafos» filtra por autor;
  - el recorrido queda ligado al padre que lo inició;
  - la app incluye las pantallas de revisión y de cuentas del supervisor.
- [ ] **Integrar las ramas.**
  - En el back, revisar y hacer merge de `autoria` → `navegacion` → `develop`.
  - En el front, hacer merge de `autoria` hacia `develop`.
- [ ] **Prueba de punta a punta con la app de la cuidadora (`ui-consulta`)** sobre las ramas integradas.

### Importantes
- [x] **Despublicar** un subgrafo publicado (`POST /subgrafos/{id}/despublicar`). Quita su opción y su nodo `ref_<grafoId>` del grafo raíz, borra su fila y su base de reglas, guarda una versión nueva del contenido y los borradores (lienzo y reglas) vuelven a `BORRADOR` con su documento intacto (se pueden corregir y republicar, o borrar). Los recorridos en curso no se cortan: cada cuidadora termina con la versión con la que empezó. No se deja despublicar el único subgrafo publicado.
- [ ] **Retiro urgente** (opcional, más adelante): cortar también los recorridos en curso por un subgrafo retirado por un error clínico grave. Hoy quien ya iba por él sigue viéndolo hasta que termine.
- [ ] **Cambiar la categoría** después de crear. Hoy se muestra pero no se edita; habría que mover la opción entre preguntas puente al republicar.
- [ ] **Vista previa como cuidadora** antes de publicar: recorrer el subgrafo con el mismo motor sin publicarlo.
- [x] **Revisión clínica** antes de publicar (rama `seguridad`): el médico envía a revisión y un supervisor que no sea el autor aprueba (y eso publica) o rechaza con un comentario. El supervisor que es autor publica directamente sus propios grafos con `POST /subgrafos/{id}/publicar`.
- [x] **Limpieza de versiones del contenido e imágenes sin uso.** Una tarea diaria (3:30; `signbb.limpieza.cron`, se apaga con `signbb.limpieza.activa=false`) sin endpoint. Versiones: conserva las 20 más recientes (la vigente siempre) y las reemplazadas hace menos de 25 h; borra el resto de la base, de Redis y de la memoria. Imágenes: borra de Silo las que no están en ningún lienzo, copia publicada, grafo ni versión conservada, y nunca las subidas hace menos de 24 h ni archivos con otro nombre. Ante la duda no borra. Código en `mantenimiento/`.
- [ ] **Historial de versiones y auditoría:** quién cambió qué y cuándo, y volver a una versión publicada anterior.
- [ ] **Administrar categorías desde la interfaz.** Hoy se agregan editando `grafo_raiz.json`, que existe en dos copias que deben quedar idénticas, y requiere volver a sembrar la base.

### Detalles menores
- [ ] Si falla la carga de la pregunta puente en el lienzo, no se avisa: el modal de publicar simplemente no la nombra.
- [ ] Pruebas e2e del front (tablero → lienzo → reglas → publicar). Hoy solo hay unitarias con Karma. Simón dejó anotado evaluar Selenium.
- [ ] Revisar el lienzo con subgrafos grandes (scroll y zoom) y en pantallas pequeñas.
- [ ] Accesibilidad del lienzo: navegar con teclado entre nodos.

## 7. Cosas a tener en cuenta

- **El compositor poda las preguntas raíz sin opciones.** Por eso una categoría nueva aparece sola cuando alguien publica en ella.
- **`grafo_raiz.json` está en dos lugares:** `backend/src/main/resources/static/grafo_sistema/` y `grafo_sistema/`. Deben quedar iguales.
- **No commitear `docs/html` generado en macOS.** Lo regenera el bot de Doxygen en el CI, y en macOS salen archivos duplicados. Por lo mismo, hay que hacer `git pull --rebase` antes de pushear al back, porque el bot sube commits.
- **El front tiene un presupuesto de 4 kB por estilo de componente.** Los estilos compartidos de los modales y los campos están en `core/ui/_modal.scss` y `core/ui/_campo.scss`.
