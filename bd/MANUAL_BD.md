# Manual de la base de datos

Servicios definidos en `compose.yaml`:

- **db**: PostgreSQL 16 (Alpine) en el puerto `5432`, con healthcheck.
- **adminer**: cliente web de administración en el puerto `8180`.

Los datos persisten en el volumen `postgres_data` (Docker lo lista como
`bd_postgres_data`).

## Levantar

Desde este directorio (`bd`):

    docker compose up -d           # levanta db y adminer
    docker compose up -d --wait    # igual, esperando a que db quede healthy
    docker compose ps              # verificar el estado de los servicios

## Dar de baja

Detiene y elimina los contenedores, **conservando** los datos del volumen:

    docker compose down

## Eliminar los datos persistentes

Da de baja los servicios y además borra el volumen con los datos:

    docker compose down -v

> Advertencia: el borrado es irreversible. Tras vaciar la base, el siguiente
> arranque del backend vuelve a crear las tablas y siembra de nuevo el grafo
> clínico y las reglas de ejemplo (`DatabaseInit`), pues la siembra solo se
> omite cuando las tablas ya tienen filas.

## Acceso rápido

- **Adminer**: `http://localhost:8180` — servidor `db`, base `signbb`,
  usuario `admin`, contraseña `example`.
- **psql**: `docker exec -it bd-db-1 psql -U admin -d signbb`
- **Logs**: `docker compose logs -f db`

## Parámetros de conexión (perfil `docker` del backend)

| Parámetro   | Valor              |
| ----------- | ------------------ |
| Host        | `localhost:5432`   |
| Base        | `signbb`           |
| Usuario     | `admin`            |
| Contraseña  | `example`          |
