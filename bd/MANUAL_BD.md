# Manual de la base de datos

Servicios definidos en `compose.yaml`:

- **db**: PostgreSQL 16 (Alpine) en el puerto `5432`, con healthcheck.
- **adminer**: cliente web de administración en el puerto `8180`.
- **silo**: almacenamiento de objetos compatible con la API de Amazon S3,
  con la API en el puerto `9000` y la consola web en el `9001`.

Los datos persisten en los volúmenes `postgres_data` y `silo_data` (Docker
los lista como `bd_postgres_data` y `bd_silo_data`).

## Levantar

Desde este directorio (`bd`):

    docker compose up -d           # levanta db, adminer y silo
    docker compose up -d --wait    # igual, esperando a que queden healthy
    docker compose ps              # verificar el estado de los servicios

## Dar de baja

Detiene y elimina los contenedores, **conservando** los datos de los
volúmenes:

    docker compose down

## Eliminar los datos persistentes

Da de baja los servicios y además borra los volúmenes con los datos
(base de datos y objetos de silo):

    docker compose down -v

> Advertencia: el borrado es irreversible. Tras vaciar la base, el siguiente
> arranque del backend vuelve a crear las tablas y siembra de nuevo el grafo
> clínico y las reglas de ejemplo (`DatabaseInit`), pues la siembra solo se
> omite cuando las tablas ya tienen filas. Los objetos borrados de silo no
> se recuperan de ninguna otra fuente.

## Acceso rápido

- **Adminer**: `http://localhost:8180` — servidor `db`, base `signbb`,
  usuario `admin`, contraseña `example`.
- **Consola de silo**: `http://localhost:9001` — usuario `silo-admin`,
  contraseña `ejemplo123`.
- **psql**: `docker exec -it bd-db-1 psql -U admin -d signbb`
- **Logs**: `docker compose logs -f db` (o `silo`, según interese)

## Parámetros de conexión (perfil `docker` del backend)

| Parámetro   | Valor              |
| ----------- | ------------------ |
| Host        | `localhost:5432`   |
| Base        | `signbb`           |
| Usuario     | `admin`            |
| Contraseña  | `example`          |

Silo expone la API S3 en `http://localhost:9000` con las mismas credenciales
de la consola (`silo-admin` / `ejemplo123`).
