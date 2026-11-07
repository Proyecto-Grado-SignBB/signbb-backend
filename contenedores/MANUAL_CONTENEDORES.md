# Manual de contenedores

Servicios definidos en `compose.yaml`:

- **db**: PostgreSQL 16 (Alpine) en el puerto `5432`, con healthcheck.
- **adminer**: cliente web de administración en el puerto `8180`.
- **silo**: almacenamiento de objetos compatible con la API de Amazon S3,
  con la API en el puerto `9000` y la consola web en el `9001`.
- **cache**: Redis 8 (Alpine) en el puerto `6379`, con healthcheck. Caché de
  los motores de navegación y reglas: guarda el contenido clínico activo
  (copia caliente de lo que vive en PostgreSQL) y las sesiones temporales de
  los recorridos en curso.
- **backend**: la API de SignBB (Spring Boot), construida con
  `backend/Dockerfile`, en el puerto `8080`. Espera a que `db`, `cache` y
  `silo` estén sanos, se reinicia sola si cae (`restart: always`) y su
  healthcheck consulta `/actuator/health`.

El frontend PWA (`ui-consulta`) se despliega aparte, en
`signbb-frontend/deploy/`: una imagen con el build de producción y Caddy, que
sirve la PWA por HTTPS y hace proxy de `/api` y `/media` al backend. No forma
parte de este `compose.yaml` porque vive en el repositorio del frontend.

Los datos persisten en los volúmenes `postgres_data`, `silo_data` y
`redis_data` (Docker los lista con el prefijo del proyecto, p. ej.
`contenedores_postgres_data`). En Redis la persistencia es AOF
(`--appendonly yes`): las sesiones sobreviven reinicios del propio
contenedor, no solo del backend.

## Levantar

Desde este directorio (`contenedores`):

    docker compose up -d --build   # levanta todo, backend incluido (lo recompila)
    docker compose up -d --wait    # igual, esperando a que queden healthy
    docker compose ps              # verificar el estado de los servicios
    curl localhost:8080/actuator/health   # {"status":"UP"} cuando el backend está listo

Para desarrollar con el backend en el host (`./mvnw spring-boot:run` desde
`backend`), se levanta solo la infraestructura:

    docker compose up -d db adminer silo cache

Si el backend del contenedor está corriendo, ocupa el puerto `8080`; detenlo
antes con `docker compose stop backend`.

### Frontend PWA (Caddy)

La PWA de consulta se levanta desde `signbb-frontend/deploy/` con su propio
compose (imagen de Caddy + build de `ui-consulta`):

    cd signbb-frontend/deploy
    cp .env.example .env      # SIGNBB_DOMINIO y SIGNBB_BACKEND
    docker compose up -d --build

El servicio `web` usa `network_mode: host` para escuchar en 80/443 y llegar al
backend por `127.0.0.1:8080`, tanto si el backend corre en el host como si es
el servicio `backend` de este compose (que publica `127.0.0.1:8080`). Caddy
escucha solo en el 443 (la redirección desde el 80 está desactivada, así que
nginx puede seguir en el 80). Si se quiere redirección `http://` → `https://`,
liberar el 80 y quitar `auto_https disable_redirects` del `Caddyfile`.

El certificado es de la CA interna de Caddy (`tls internal`), porque no hay
certificados públicos para IPs privadas. La CA raíz se copia del volumen
`signbb_caddy_data` y se instala una vez en cada dispositivo:

    docker compose cp web:/data/caddy/pki/authorities/local/root.crt ./caddy-root.crt

El detalle de instalación en Android/iOS, los comandos de operación, el
backup de la CA, la actualización del frontend y el diagnóstico están en
`signbb-frontend/deploy/README.md`.

Para que Spring funcione detrás de Caddy, `application.properties` activa
`server.forward-headers-strategy=framework` por defecto (se puede desactivar
con `SERVER_FORWARD_HEADERS_STRATEGY=none`). En producción, añadir también el
origen HTTPS a CORS:

    export SIGNBB_CORS_ORIGENES=https://192.168.20.217,http://localhost:4200,http://localhost:4300
    ./mvnw spring-boot:run

El mismo `SIGNBB_CORS_ORIGENES` se puede definir en el `.env` de este compose.
No se debe borrar `signbb_caddy_data` sin reinstalar la CA en los móviles: la
CA cambia y los certificados anteriores dejan de ser válidos.

### Variables de entorno

Todas tienen un valor de desarrollo, así que `docker compose up` funciona sin
configurar nada. Para cambiarlas, exportarlas antes de levantar o escribirlas en
un archivo `.env` en este directorio (no se versiona). Las que comparten
nombre se usan en dos servicios a la vez, para que no queden distintas.

| Variable | Servicios | Valor de desarrollo |
| -------- | --------- | ------------------- |
| `SIGNBB_DB_CONTRASENA` | db, backend | `example` |
| `SIGNBB_MEDIA_ACCESS_KEY` | silo, backend | `silo-admin` |
| `SIGNBB_MEDIA_SECRET_KEY` | silo, backend | `ejemplo123` |
| `SIGNBB_ENTORNO` | backend | `desarrollo` |
| `SIGNBB_JWT_SECRETO` | backend | valor fijo de desarrollo |
| `SIGNBB_SUPERVISOR_CONTRASENA` | backend | `Supervisor123` |
| `SIGNBB_DOCS_ACTIVAS` | backend | `true` |
| `SIGNBB_SIEMBRA_ACTIVA` | backend | `true` |
| `SIGNBB_SEED_DEMO_ACTIVA` | backend | `false` |
| `SIGNBB_SEED_MEDICO_NOMBRE` | backend | `Médico ejemplo - Neonatologo` |
| `SIGNBB_SEED_MEDICO_CORREO` | backend | `medico.ejemplo@signbb.local` |
| `SIGNBB_SEED_MEDICO_CONTRASENA` | backend | `Medico123` |
| `SIGNBB_SEED_MEDICO_INSTITUCION` | backend | `Fundación Canguro` |
| `SIGNBB_SEED_PADRE_NOMBRE` | backend | `Padre ejemplo` |
| `SIGNBB_SEED_PADRE_CORREO` | backend | `padre.ejemplo@signbb.local` |
| `SIGNBB_SEED_PADRE_CONTRASENA` | backend | `Padre123` |
| `SIGNBB_CORS_ORIGENES` | backend | `http://localhost:4200,http://localhost:4300` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | backend | `framework` (valor por defecto) |

> PostgreSQL y Silo leen su contraseña solo la primera vez, con el volumen
> vacío. Si se cambia después, hay que cambiarla también dentro del servicio o
> empezar de cero con `docker compose down -v`.

**Producción.** `SIGNBB_ENTORNO=produccion` obliga a dar valores propios a los
secretos y `SIGNBB_DOCS_ACTIVAS=false`; si no, el backend no arranca y el log
dice qué variable falta. `SIGNBB_SIEMBRA_ACTIVA=true` solo en el primer
despliegue (siembra el grafo raíz; los subgrafos se publican después) y
`false` después. `SIGNBB_SEED_DEMO_ACTIVA` debe quedar en `false`: crea cuentas
de ejemplo y publica los cuatro subgrafos de demo. Para sembrar el entorno de
demo, enciéndelo junto con la siembra y luego bórralo o ponlo en `false`.

**Seed de demo.** Con `SIGNBB_SEED_DEMO_ACTIVA=true` se crean:

- Médico: `medico.ejemplo@signbb.local` / `Medico123`, autor de los 4 subgrafos.
- Padre: `padre.ejemplo@signbb.local` / `Padre123`.

El seed es idempotente. Para volver a sembrarlo desde cero hay que borrar
PostgreSQL y Redis (`docker compose down -v`), porque las versiones y el caché
pueden conservar contenido anterior.

### Ejecutar con seed de datos
    SIGNBB_SEED_DEMO_ACTIVA=true docker compose up -d --build

## Dar de baja

Detiene y elimina los contenedores, **conservando** los datos de los
volúmenes:

    docker compose down

## Eliminar los datos persistentes

Da de baja los servicios y además borra los volúmenes con los datos (base de
datos, objetos de silo y caché/sesiones de Redis):

    docker compose down -v

> Advertencia: el borrado es irreversible. Tras vaciar la base, el siguiente
> arranque del backend vuelve a crear las tablas con las migraciones de Flyway
> y, si `SIGNBB_SIEMBRA_ACTIVA` es `true`, siembra **solo el grafo raíz**.
> Los subgrafos y sus bases de reglas ya no se cargan automáticamente: se
> crean y publican desde la app de autoría o se importan con el seed de datos
> de pruebas/demo. Las sesiones de recorridos en curso guardadas en Redis y
> los objetos de silo no se recuperan de ninguna otra fuente.
>
> Actualizar el contenido clínico: `DatabaseInit` decide sembrar según el `id`
> del grafo raíz (`grafo_raiz`). Si se cambian los archivos del grafo raíz pero
> la base ya estaba sembrada, hay que borrar los volúmenes
> (`docker compose down -v`) y volver a levantar; los subgrafos publicados
> también se pierden.
>
> Base local vieja: si el backend no arranca porque Hibernate dice que una
> tabla o columna no coincide con el esquema, la base se creó antes de Flyway
> con otra forma. `docker compose down -v` y volver a levantar la deja migrada.

## Acceso rápido

- **Adminer**: `http://localhost:8180` — servidor `db`, base `signbb`,
  usuario `admin`, contraseña `example`.
- **Consola de silo**: `http://localhost:9001` — usuario `silo-admin`,
  contraseña `ejemplo123`.
- **redis-cli**: `docker exec -it contenedores-cache-1 redis-cli`
- **psql**: `docker exec -it contenedores-db-1 psql -U admin -d signbb`
- **Logs**: `docker compose logs -f db` (o `silo` / `cache` / `backend`, según interese)

> **Puertos solo locales.** PostgreSQL, Adminer, Silo, Redis y el backend se publican en
> `127.0.0.1` (por ejemplo `127.0.0.1:5432`). Funcionan desde tu propio
> computador, pero no se ven desde otro equipo de la red. Si cambias el
> `compose.yaml`, aplica el cambio con `docker compose up -d`: los contenedores
> se recrean y los datos se conservan porque viven en volúmenes.

## Parámetros de conexión (perfil `docker` del backend en el host)

| Parámetro   | Valor              |
| ----------- | ------------------ |
| Host        | `localhost:5432`   |
| Base        | `signbb`           |
| Usuario     | `admin`            |
| Contraseña  | `example`          |

Silo expone la API S3 en `http://localhost:9000` con las mismas credenciales
de la consola (`silo-admin` / `ejemplo123`). El cache Redis escucha en
`localhost:6379` sin contraseña (solo desarrollo).
