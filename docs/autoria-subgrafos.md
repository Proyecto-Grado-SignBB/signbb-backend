# SignBB · Autoría de subgrafos clínicos: qué se hizo y qué falta

> Resumen para el equipo · actualizado el 10-oct-2026

## 1. Qué es esta parte

El **médico** arma en un lienzo un *subgrafo clínico*, es decir, el camino de preguntas que lleva a una conclusión para un signo. Al **publicarlo**, ese subgrafo aparece como una opción nueva en la app de la **cuidadora**, dentro de la categoría que eligió el médico.

```
Cuidadora:  ¿Qué le pasa al bebé?  ──piel──▶  ¿Qué cambio observa en la piel o los ojos?
                                                 └─ «Piel u ojos amarillos»  ◀── lo agrega el médico al publicar
                                                       └─ preguntas del subgrafo … → Recomendación / Consulta / Urgencia
```

Esta entrega cubre subgrafos **sin reglas clínicas**. El desenlace lo decide la conclusión a la que se llega (ver pendientes).

## 2. Ramas y cómo están

| Repo | Rama | Sale de | Último commit | Estado |
|---|---|---|---|---|
| `signbb-backend` | `autoria` | `navegacion` (11 commits por delante) | `c23c5e7` | Pusheada, CI verde (297 pruebas) |
| `signbb-frontend` | `autoria-lienzo` | `autoria` (la rama de Daniela, 5 commits por delante) | `fa3b46e` | Pusheada, 26 pruebas verdes y build limpio |

- **No se ha hecho merge ni PR** de ninguna de las dos ramas.
- La rama `feature/modulo-autoria` del back y la `autoria` del front **no se tocaron**.
- El formato con Y/O anidado vive aparte en `feature/motor-and-or` y quedó diferido.

## 3. Pasos que seguimos

### Back (`signbb-backend`, rama `autoria`)
1. **Modelo y persistencia del borrador** (`c545730`): `BorradorSubgrafoEntity`, que guarda el lienzo como JSON (`DocumentoLienzo`, `NodoLienzo`, `RespuestaLienzo`). Lleva `@Version` para detectar ediciones simultáneas y los estados `BORRADOR`, `PUBLICADO` y `CAMBIOS_SIN_PUBLICAR`. Es independiente del grafo publicado.
2. **Edición del lienzo y avisos** (`25ec6e2`):
   - `EditorLienzo` maneja el "+", el enlace a una pregunta existente (sin permitir círculos) y editar o eliminar nodos (podando la rama huérfana).
   - `ValidadorLienzo` genera avisos en lenguaje humano. Algunos bloquean la publicación y otros solo advierten, como un nodo suelto.
3. **Compilar y publicar** (`3733bad`):
   - `CompiladorSubgrafo` convierte el lienzo en un `GrafoClinicoJson`. El peso es la mayor gravedad que llega al nodo, y el tipo de selección (única o múltiple) se respeta.
   - `PublicadorSubgrafo` guarda el subgrafo y agrega su opción y el nodo `ref_<grafoId>` en la pregunta puente del grafo raíz. Republicar no duplica nada.
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

### Front (`signbb-frontend`, rama `autoria-lienzo`, proyecto `ui-autoria`)
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

## 4. API (`/api/autoria`)

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/categorias` | Preguntas puente del grafo raíz |
| GET / POST | `/subgrafos` | Listar / crear (nombre, puenteId, etiquetaOpcion, autor, institucion) |
| GET / PUT / DELETE | `/subgrafos/{id}` | Ver / editar datos / borrar (solo si nunca se publicó) |
| POST | `/subgrafos/{id}/nodos` | Crear nodo (el "+") |
| PUT / DELETE | `/subgrafos/{id}/nodos/{nodoId}` | Editar / eliminar nodo |
| PUT | `/subgrafos/{id}/respuestas/{respuestaId}/destino` | Enlazar a una pregunta existente (`{"nodoId": null}` lo quita) |
| GET | `/subgrafos/{id}/avisos` | Avisos actuales |
| POST | `/subgrafos/{id}/publicar` | Publicar (422 con avisos si hay bloqueantes) |
| POST | `/subgrafos/{id}/despublicar` | Despublicar: quita el subgrafo y su base de reglas; los borradores (lienzo y reglas) vuelven a `BORRADOR` con su documento intacto (422 si no está publicado o si es el único subgrafo publicado) |

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
git fetch origin && git checkout autoria-lienzo && git pull
npm install
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
git fetch origin; git checkout autoria-lienzo; git pull
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
- [ ] **Reglas clínicas en los subgrafos.** Hoy el desenlace sale solo de la conclusión. Falta poder definir la base de reglas (casos en FND: OR de casos, AND interno) desde la autoría y que se publique junto al subgrafo. La pantalla de reglas vieja del front se quitó porque usaba la API anterior. Hay que decidir si se usa el formato plano actual o el Y/O anidado de `feature/motor-and-or`.
- [ ] **Autenticación de médicos.** El autor y la institución están fijos en el front («Dra. Catalina Rodríguez», «Fundación Canguro»). Hace falta un login real, que el autor salga de la sesión y que «Mis subgrafos» y el perfil filtren por autor.
- [ ] **Integrar las ramas.**
  - En el back, revisar y hacer merge de `autoria` → `navegacion` → `develop`.
  - En el front, decidir con Daniela cómo entra `autoria-lienzo` en `autoria`, porque reemplaza las pantallas de `features/grafos`.
- [ ] **Prueba de punta a punta con la app de la cuidadora (`ui-consulta`)** sobre las ramas integradas.

### Importantes
- [x] **Despublicar** un subgrafo publicado (`POST /subgrafos/{id}/despublicar`). Quita su opción y su nodo `ref_<grafoId>` del grafo raíz, borra su fila y su base de reglas, guarda una versión nueva del contenido y los borradores (lienzo y reglas) vuelven a `BORRADOR` con su documento intacto (se pueden corregir y republicar, o borrar). Los recorridos en curso no se cortan: cada cuidadora termina con la versión con la que empezó. No se deja despublicar el único subgrafo publicado.
- [ ] **Retiro urgente** (opcional, más adelante): cortar también los recorridos en curso por un subgrafo retirado por un error clínico grave. Hoy quien ya iba por él sigue viéndolo hasta que termine.
- [ ] **Cambiar la categoría** después de crear. Hoy se muestra pero no se edita; habría que mover la opción entre preguntas puente al republicar.
- [ ] **Vista previa como cuidadora** antes de publicar: recorrer el subgrafo con el mismo motor sin publicarlo.
- [ ] **Revisión o aprobación clínica** antes de publicar, por ejemplo que otro médico apruebe. Hoy cualquiera publica directo.
- [x] **Limpieza de versiones del contenido e imágenes sin uso.** Una tarea diaria (3:30; `signbb.limpieza.cron`, se apaga con `signbb.limpieza.activa=false`) sin endpoint. Versiones: conserva las 20 más recientes (la vigente siempre) y las reemplazadas hace menos de 25 h; borra el resto de la base, de Redis y de la memoria. Imágenes: borra de Silo las que no están en ningún lienzo, copia publicada, grafo ni versión conservada, y nunca las subidas hace menos de 24 h ni archivos con otro nombre. Ante la duda no borra. Código en `mantenimiento/`.
- [ ] **Historial de versiones y auditoría:** quién cambió qué y cuándo, y volver a una versión publicada anterior.
- [ ] **Administrar categorías desde la interfaz.** Hoy se agregan editando `grafo_raiz.json`, que existe en dos copias que deben quedar idénticas, y requiere volver a sembrar la base.

### Detalles menores
- [ ] Si falla la carga de la pregunta puente en el lienzo, no se avisa: el modal de publicar simplemente no la nombra.
- [ ] Pruebas e2e del front (tablero → lienzo → publicar). Hoy solo hay unitarias con Karma.
- [ ] Revisar el lienzo con subgrafos grandes (scroll y zoom) y en pantallas pequeñas.
- [ ] Accesibilidad del lienzo: navegar con teclado entre nodos.

## 7. Cosas a tener en cuenta

- **El compositor poda las preguntas raíz sin opciones.** Por eso una categoría nueva aparece sola cuando alguien publica en ella.
- **`grafo_raiz.json` está en dos lugares:** `backend/src/main/resources/static/grafo_sistema/` y `grafo_sistema/`. Deben quedar iguales.
- **No commitear `docs/html` generado en macOS.** Lo regenera el bot de Doxygen en el CI, y en macOS salen archivos duplicados. Por lo mismo, hay que hacer `git pull --rebase` antes de pushear al back, porque el bot sube commits.
- **El front tiene un presupuesto de 4 kB por estilo de componente.** Los estilos compartidos de los modales y los campos están en `core/ui/_modal.scss` y `core/ui/_campo.scss`.
