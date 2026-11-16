# SignBB — Backend

Backend del sistema SignBB: triaje clínico guiado por un grafo de navegación
y un motor de reglas.

## Requisitos

- Java SDK 25
- Spring Boot 4.1.1 (Web MVC, Data JPA, Redis, Flyway, Actuator, Lombok)
- Maven (se usa el wrapper `backend/mvnw`, no requiere instalación local)
- Docker + Docker Compose (PostgreSQL y Redis de `contenedores/compose.yaml`)
- Doxygen (solo para regenerar la documentación)

## Construcción y pruebas

Desde el directorio `backend`:

    ./mvnw test              # compila y ejecuta la suite de tests
    ./mvnw spring-boot:run   # arranca la aplicación
    ./mvnw verify            # build completo, igual que en CI (GitHub Actions)

La demo del motor por consola (`DemoTriajeRunner`) está apagada; se enciende
con `SIGNBB_DEMO_ACTIVA=true`. Necesita los subgrafos de ejemplo de
`src/test/resources`; en el jar de runtime no están y la demo se omite.

### Pruebas

La suite requiere PostgreSQL y Redis. Desde `backend`:

```bash
./mvnw test
./mvnw -Dtest=MotorNavegacionTest test
./mvnw -Dtest=MotorNavegacionTest#metodo test
./mvnw verify
```

Los resultados quedan en `target/surefire-reports`. La suite está descrita en
`backend/src/test/PRUEBAS.md` y en Doxygen (`\ref pruebas`).

Las pruebas de punta a punto que usan el seed de demo se describen en
[`docs/entorno-pruebas/entorno-pruebas.md`](docs/entorno-pruebas/entorno-pruebas.md).

## Perfil de base de datos

El backend usa un único perfil, `docker` (definido en
`backend/src/main/resources/application.properties` con
`spring.profiles.active=docker`): **PostgreSQL** en `localhost:5432` (base
`signbb`) y **Redis** en `localhost:6379`, ambos desde
`contenedores/compose.yaml`. Los datos persisten en los volúmenes del compose.
Ver `contenedores/MANUAL_CONTENEDORES.md` para levantar y administrar los
servicios.

Hay dos formas de trabajar:

- **Todo en contenedores** (backend incluido, construido con
  `backend/Dockerfile`): `cd contenedores && docker compose up -d --build`. La
  API queda en `http://localhost:8080` y `curl localhost:8080/actuator/health`
  responde `{"status":"UP"}`.
- **Backend en el host** (para desarrollar): se levanta solo la
  infraestructura y se arranca con Maven.

      cd ../contenedores && docker compose up -d db adminer silo cache

      ./mvnw spring-boot:run   # arranca la aplicación
      ./mvnw test              # suite completa contra PostgreSQL y Redis

**Esquema con Flyway.** Las tablas las crean las migraciones de
`backend/src/main/resources/db/migration` (`V1__esquema_inicial.sql`,
`V2__telefono_pediatra.sql`, `V3__integridad_e_indices.sql`, ...); Hibernate solo valida
(`ddl-auto=validate`). Un cambio de esquema es un archivo `V<n>__descripcion.sql`
nuevo; nunca se edita una migración ya publicada. Una base creada antes de
Flyway se marca como versión 1 y recibe solo las siguientes. Si al arrancar
Hibernate dice que una tabla o columna no coincide (bases locales muy viejas),
borra los volúmenes con `docker compose down -v`.

> `DatabaseInit` solo comprueba la existencia de `grafo_raiz`. Si cambian los
> archivos del grafo raíz en una base ya sembrada, hay que borrar los volúmenes
> (`docker compose down -v`) y volver a levantar; eso también borra los
> subgrafos publicados y las sesiones de Redis. Los subgrafos de ejemplo ya no
> se siembran.

**Contenido inicial.** `DatabaseInit` siembra solo el grafo raíz
(`static/grafo_sistema/grafo_raiz.json`) cuando la base no tiene `grafo_raiz`;
no crea subgrafos ni bases de reglas. Los subgrafos y sus reglas se publican
desde la app de autoría o se cargan con el seed de datos de pruebas/demo.
Mientras no exista al menos un subgrafo publicado, la app de consulta muestra
«La consulta no está disponible en estos momentos» y deshabilita el inicio;
`POST /api/recorridos` responde 503.

## Cuentas y seguridad

La API pide un token JWT (`Authorization: Bearer <token>`) salvo en el login, el
registro, `/media`, Swagger y `GET /actuator/health` (solo dice `UP` o `DOWN`;
el resto de Actuator no se expone). Hay tres roles:

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

**Seed de demo.** Con `SIGNBB_SEED_DEMO_ACTIVA=true`, después de la siembra del
grafo raíz, el backend crea el médico y el padre de ejemplo y publica los cinco
subgrafos de `src/main/resources/seed/demo/` usando el flujo real de autoría. El
médico queda como autor de los subgrafos y de sus bases de reglas. Cuentas de
desarrollo:

- Médico: `medico.ejemplo@signbb.local` / `Medico123`, institución
  `Fundación Canguro`.
- Padre: `padre.ejemplo@signbb.local` / `Padre123`.

El seed es idempotente y está apagado por defecto. Si cambian los JSON del seed
y quieres volver a sembrar desde cero, borra PostgreSQL y Redis
(`docker compose down -v`).

El detalle de usuarios, grafos, bases de reglas y scripts está en
[`docs/entorno-pruebas/entorno-pruebas.md`](docs/entorno-pruebas/entorno-pruebas.md).

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
| `SIGNBB_SHOW_SQL` | `true` imprime el SQL en consola. En producción la app no arranca si está en `true` | `false` |
| `SIGNBB_SIEMBRA_ACTIVA` | Siembra solo el grafo raíz si falta (`DatabaseInit`). En producción, `true` solo en el primer despliegue | `true` |
| `SIGNBB_SEED_DEMO_ACTIVA` | Crea las cuentas de demo y publica los 5 subgrafos de `seed/demo/`. **No usar en producción** | `false` |
| `SIGNBB_SEED_MEDICO_NOMBRE` | Nombre del médico de demo | `Médico ejemplo - Neonatologo` |
| `SIGNBB_SEED_MEDICO_CORREO` | Correo del médico de demo | `medico.ejemplo@signbb.local` |
| `SIGNBB_SEED_MEDICO_CONTRASENA` | Contraseña del médico de demo | `Medico123` |
| `SIGNBB_SEED_MEDICO_INSTITUCION` | Institución del médico de demo | `Fundación Canguro` |
| `SIGNBB_SEED_PADRE_NOMBRE` | Nombre del padre de demo | `Padre ejemplo` |
| `SIGNBB_SEED_PADRE_CORREO` | Correo del padre de demo | `padre.ejemplo@signbb.local` |
| `SIGNBB_SEED_PADRE_CONTRASENA` | Contraseña del padre de demo | `Padre123` |
| `SIGNBB_DEMO_ACTIVA` | Imprime la demo del motor al arrancar (`DemoTriajeRunner`) | `false` |
| `SIGNBB_DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/signbb` |
| `SIGNBB_DB_USUARIO` | Usuario de PostgreSQL | `admin` |
| `SIGNBB_DB_CONTRASENA` | Contraseña de PostgreSQL | `example` |
| `SIGNBB_REDIS_HOST` | Host de Redis | `localhost` |
| `SIGNBB_REDIS_PUERTO` | Puerto de Redis | `6379` |
| `SIGNBB_MEDIA_ENDPOINT` | URL de la API S3 de Silo | `http://localhost:9000` |
| `SIGNBB_MEDIA_BUCKET` | Bucket de las imágenes | `signbb-imagenes` |

En el contenedor, `contenedores/compose.yaml` ya apunta las de conexión a los
servicios `db`, `cache` y `silo`; el resto se toma del entorno o de un archivo
`contenedores/.env` (no versionado).

**Despliegue en producción.** Define `SIGNBB_ENTORNO=produccion`. Con eso, al
arrancar se revisa que `SIGNBB_JWT_SECRETO`, `SIGNBB_SUPERVISOR_CONTRASENA`,
`SIGNBB_MEDIA_ACCESS_KEY` y `SIGNBB_MEDIA_SECRET_KEY` tengan valores propios, que
`SIGNBB_DOCS_ACTIVAS=false` y que `SIGNBB_SHOW_SQL` no esté en `true`. Si algo falla, la app no arranca y el mensaje dice
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
La fuente es `backend/src/main/java`, `backend/src/test/java` y los markdown de
`docs/`, configuración en el `Doxyfile`. El informe de entorno de pruebas queda
en [`docs/entorno-pruebas/entorno-pruebas.md`](docs/entorno-pruebas/entorno-pruebas.md)
y sus tablas de reglas en `docs/entorno-pruebas/reglas/`.

### Pruebas en Doxygen

La suite de pruebas está documentada en la sección \ref pruebas del Doxygen:
tipos de prueba, cómo ejecutarlas y el detalle por módulo (navegación, reglas,
modelo de reglas, autoría, observaciones y cache).

El diagrama de arquitectura por módulos (`docs/diagramas/arquitectura_modulos.svg`)
se regenera desde su fuente PlantUML:

    java -jar plantuml.jar -tsvg docs/diagramas/arquitectura_modulos.puml

### Grafos y reglas

Los DOT/SVG, los JSON y las tablas de reglas de la documentación viven en
`docs/entorno-pruebas/`. Para regenerar todos los SVG y las tablas Markdown:

```bash
cd docs/entorno-pruebas
./scripts/regenerar_grafos.sh
```

También se puede regenerar un grafo suelto:

```bash
dot -Tsvg grafos/raiz.dot -o grafos/raiz.svg
```

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
