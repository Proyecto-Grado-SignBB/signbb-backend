# SignBB — Backend

Backend del sistema SignBB: triaje clínico guiado por un grafo de navegación
y un motor de reglas.

## Requisitos

- Java SDK 25
- Spring Boot 4.1.1 (Web MVC, Data JPA, Redis, Lombok)
- Maven (se usa el wrapper `backend/mvnw`, no requiere instalación local)
- Docker + Docker Compose (PostgreSQL y Redis de `contenedores/compose.yaml`)
- Doxygen (solo para regenerar la documentación)

## Construcción y pruebas

Desde el directorio `backend`:

    ./mvnw test              # compila y ejecuta la suite de tests
    ./mvnw spring-boot:run   # arranca la aplicación (demo de triaje por consola)
    ./mvnw verify            # build completo, igual que en CI (GitHub Actions)

## Perfil de base de datos

El backend usa un único perfil, `docker` (definido en
`backend/src/main/resources/application.properties` con
`spring.profiles.active=docker`): **PostgreSQL** en `localhost:5432` (base
`signbb`) y **Redis** en `localhost:6379`, ambos desde
`contenedores/compose.yaml`. Los datos persisten en los volúmenes del compose.
Ver `contenedores/MANUAL_CONTENEDORES.md` para levantar y administrar los
servicios.

Antes de arrancar la aplicación o ejecutar las pruebas hay que levantar los
servicios:

    cd ../contenedores && docker compose up -d

    ./mvnw spring-boot:run   # arranca la aplicación (demo de triaje por consola)
    ./mvnw test              # suite completa contra PostgreSQL y Redis

> Si la base ya estaba sembrada con una versión previa del grafo, hay que
> borrar los volúmenes (`docker compose down -v`) para que `DatabaseInit`
> vuelva a sembrar el contenido nuevo: el grafo raíz conserva el mismo `id`.

## Cuentas y seguridad

La API pide un token JWT (`Authorization: Bearer <token>`) salvo en el login, el
registro, `/media` y Swagger. Hay tres roles:

| Rol | Cómo se crea | Qué puede hacer |
|---|---|---|
| `PADRE` | Se registra solo (`POST /api/auth/registro`) | Hacer recorridos y ver los suyos (`/api/recorridos`) |
| `MEDICO` | Lo crea un supervisor (`POST /api/usuarios`) | Armar sus subgrafos y reglas y enviarlos a revisión |
| `MEDICO_SUPERVISOR` | Lo crea otro supervisor; el primero se siembra al arrancar | Lo mismo que un médico, y además publicar directamente sus propios grafos, aprobar o rechazar lo de **otros**, despublicar y administrar cuentas |

Para ingresar: `POST /api/auth/login` con `{"correo", "contrasena"}` devuelve
`{token, expiraEn, usuario}`. El token dura 8 horas.

**Supervisor inicial.** Si no existe ningún supervisor, el back crea uno al
arrancar. En desarrollo es `supervisor@signbb.local` / `Supervisor123`;
**cámbialo** fuera de tu máquina con las variables de abajo.

**Variables de entorno:**

| Variable | Para qué | Valor de desarrollo |
|---|---|---|
| `SIGNBB_ENTORNO` | `desarrollo` o `produccion`. En producción la app no arranca si quedan valores de ejemplo (ver abajo) | `desarrollo` |
| `SIGNBB_JWT_SECRETO` | Clave HS256 de los tokens (32 bytes o más) | un valor fijo de desarrollo |
| `SIGNBB_SUPERVISOR_NOMBRE` | Nombre del supervisor inicial | `Supervisor SignBB` |
| `SIGNBB_SUPERVISOR_CORREO` | Correo del supervisor inicial | `supervisor@signbb.local` |
| `SIGNBB_SUPERVISOR_CONTRASENA` | Contraseña del supervisor inicial | `Supervisor123` |
| `SIGNBB_CORS_ORIGENES` | Orígenes del front, separados por coma | `http://localhost:4200,http://localhost:4300` |
| `SIGNBB_MEDIA_ACCESS_KEY` | Usuario de Silo (imágenes) | `silo-admin` |
| `SIGNBB_MEDIA_SECRET_KEY` | Clave de Silo (imágenes) | `ejemplo123` |
| `SIGNBB_DOCS_ACTIVAS` | `false` apaga Swagger UI y `/v3/api-docs` (responden 404). **Obligatorio en producción** | `true` |

**Despliegue en producción.** Define `SIGNBB_ENTORNO=produccion`. Con eso, al
arrancar se revisa que `SIGNBB_JWT_SECRETO`, `SIGNBB_SUPERVISOR_CONTRASENA`,
`SIGNBB_MEDIA_ACCESS_KEY` y `SIGNBB_MEDIA_SECRET_KEY` tengan valores propios, y que
`SIGNBB_DOCS_ACTIVAS=false`. Si algo falla, la app no arranca y el mensaje dice
cuál variable corregir (nunca muestra el valor). Si `SIGNBB_ENTORNO` tiene
cualquier otro texto, tampoco arranca. Sin esta variable la app se comporta como
en desarrollo.

> **Si ya tenías la base de antes de las cuentas**, borra los volúmenes
> (`docker compose down -v`). Hibernate guarda el estado del borrador con un
> *check constraint* que no conoce el estado nuevo `EN_REVISION`, y los
> borradores viejos no tienen autor.

La prueba manual del módulo (con `curl` y por las pantallas) está en
`pruebas_manuales/pruebas_seguridad.md`.

## Documentación

La documentación del código se genera con Doxygen desde la raíz del
repositorio:

    doxygen Doxyfile

El HTML resultante queda en `docs/html` (abrir `docs/html/index.html`).
La fuente es `backend/src/main/java` y `backend/src/test/java`, configurada en
el `Doxyfile`.

### Pruebas

La suite de pruebas está documentada en la sección \ref pruebas del Doxygen:
tipos de prueba, cómo ejecutarlas y el detalle por módulo (navegación, reglas,
modelo de reglas, autoría, observaciones y cache).

El diagrama de arquitectura por módulos (`docs/diagramas/arquitectura_modulos.svg`)
se regenera desde su fuente PlantUML:

    java -jar plantuml.jar -tsvg docs/diagramas/arquitectura_modulos.puml

### API REST (OpenAPI)

La API se documenta con **springdoc-openapi** a partir de los controladores.
Con la aplicación arriba:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Contrato JSON: `http://localhost:8080/v3/api-docs`
- Contrato YAML: `http://localhost:8080/v3/api-docs.yaml`

**Límites de los campos.** Los datos que llegan a la API tienen límite de largo; si se pasan, responde **422** con un `mensaje` que dice cuál campo. Los nombres, el correo, las etiquetas y los textos de las opciones son de una sola línea (sin saltos de línea); las descripciones y el texto de una pregunta permiten saltos de línea.

| Campo | Límite |
|---|---|
| Correo | 254 caracteres |
| Nombre de persona; nombre de subgrafo, de regla o de base de reglas; etiqueta para la familia | 100 |
| Institución | 150 |
| Contraseña | 8 a 72 caracteres (y hasta 72 bytes: la ñ y las tildes cuentan doble) |
| Texto de una pregunta o conclusión | 1000 |
| Texto de una opción de respuesta; respuesta del cuidador | 200 |
| Respuestas por pregunta | 10 |
| Descripción de regla o de base de reglas | 500 |
| Casos por regla / condiciones por caso | 20 / 20 |
| Opciones marcadas por el cuidador | 20 |

En producción la documentación va apagada (`SIGNBB_DOCS_ACTIVAS=false`) y
esas rutas responden 404; el contrato se consulta en la copia versionada.

La copia versionada del contrato vive en `docs/api/openapi.yaml`. Para
regenerarla tras cambiar los controladores o los DTOs:

    ./mvnw spring-boot:run   # en una terminal; esperar "Started BackendApplication"
    curl -o docs/api/openapi.yaml http://localhost:8080/v3/api-docs.yaml

Las descripciones de operaciones y esquemas se toman de las anotaciones
`@Tag`, `@Operation`, `@ApiResponse` y `@Schema` en los controladores y DTOs.
