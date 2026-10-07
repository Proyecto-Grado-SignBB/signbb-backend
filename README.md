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

## Documentación

La documentación del código se genera con Doxygen desde la raíz del
repositorio:

    doxygen Doxyfile

El HTML resultante queda en `docs/html` (abrir `docs/html/index.html`).
La fuente es `backend/src/main/java`, configurada en el `Doxyfile`.

El diagrama de arquitectura por módulos (`docs/diagramas/arquitectura_modulos.svg`)
se regenera desde su fuente PlantUML:

    java -jar plantuml.jar -tsvg docs/diagramas/arquitectura_modulos.puml
