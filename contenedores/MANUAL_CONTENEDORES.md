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

Los datos persisten en los volúmenes `postgres_data`, `silo_data` y
`redis_data` (Docker los lista con el prefijo del proyecto, p. ej.
`contenedores_postgres_data`). En Redis la persistencia es AOF
(`--appendonly yes`): las sesiones sobreviven reinicios del propio
contenedor, no solo del backend.

## Levantar

Desde este directorio (`contenedores`):

    docker compose up -d           # levanta db, adminer, silo y cache
    docker compose up -d --wait    # igual, esperando a que queden healthy
    docker compose ps              # verificar el estado de los servicios

## Dar de baja

Detiene y elimina los contenedores, **conservando** los datos de los
volúmenes:

    docker compose down

## Eliminar los datos persistentes

Da de baja los servicios y además borra los volúmenes con los datos (base de
datos, objetos de silo y caché/sesiones de Redis):

    docker compose down -v

> Advertencia: el borrado es irreversible. Tras vaciar la base, el siguiente
> arranque del backend vuelve a crear las tablas y siembra de nuevo el grafo
> clínico y las reglas de ejemplo (`DatabaseInit`), pues la siembra solo se
> omite cuando las tablas ya tienen filas. Las sesiones de recorridos en
> curso guardadas en Redis y los objetos de silo no se recuperan de ninguna
> otra fuente.

## Acceso rápido

- **Adminer**: `http://localhost:8180` — servidor `db`, base `signbb`,
  usuario `admin`, contraseña `example`.
- **Consola de silo**: `http://localhost:9001` — usuario `silo-admin`,
  contraseña `ejemplo123`.
- **redis-cli**: `docker exec -it contenedores-cache-1 redis-cli`
- **psql**: `docker exec -it contenedores-db-1 psql -U admin -d signbb`
- **Logs**: `docker compose logs -f db` (o `silo` / `cache`, según interese)

## Parámetros de conexión (perfil `docker` del backend)

| Parámetro   | Valor              |
| ----------- | ------------------ |
| Host        | `localhost:5432`   |
| Base        | `signbb`           |
| Usuario     | `admin`            |
| Contraseña  | `example`          |

Silo expone la API S3 en `http://localhost:9000` con las mismas credenciales
de la consola (`silo-admin` / `ejemplo123`). El cache Redis escucha en
`localhost:6379` sin contraseña (solo desarrollo).
