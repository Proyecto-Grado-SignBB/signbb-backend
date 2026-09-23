# SignBB — Backend

Backend del sistema SignBB: triaje clínico guiado por un grafo de navegación
y un motor de reglas.

## Requisitos

- Java SDK 25
- Spring Boot 4.1.1 (Web MVC, Data JPA, H2, Lombok)
- Maven (se usa el wrapper `backend/mvnw`, no requiere instalación local)
- Doxygen (solo para regenerar la documentación)

## Construcción y pruebas

Desde el directorio `backend`:

    ./mvnw test              # compila y ejecuta la suite de tests
    ./mvnw spring-boot:run   # arranca la aplicación (demo de triaje por consola)
    ./mvnw verify            # build completo, igual que en CI (GitHub Actions)

## Perfiles de base de datos

El backend tiene dos perfiles; el activo se define en
`backend/src/main/resources/application.properties` con la propiedad
`spring.profiles.active` (hoy `docker` por defecto):

- `dev` — **H2 en memoria** (`jdbc:h2:mem:dcbapp`): no necesita Docker y los
  datos se pierden al detener la aplicación. Consola H2 en
  `http://localhost:8080/h2-console` (usuario `sa`).
- `docker` — **PostgreSQL** en `localhost:5432` (base `signbb`): requiere la
  base de datos de `bd/compose.yaml` levantada y persiste los datos en el
  volumen del compose. Ver `bd/MANUAL_BD.md` para levantarla y administrarla.

Para cambiar de perfil en un arranque puntual, sin editar archivos, se pasa
como flag (ej. pasar de memoria a docker o viceversa):

    ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # H2 en memoria
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=docker  # PostgreSQL

Para usar docker de forma permanente, dejar `spring.profiles.active=docker`
en `application.properties` y levantar la base de datos:

    cd ../bd && docker compose up -d

Las pruebas usan el perfil activo: con `docker` la base de datos debe estar
levantada; con `dev` corren contra H2 en memoria sin ningún requisito:

    ./mvnw test                               # usa el perfil activo
    ./mvnw test -Dspring.profiles.active=dev  # fuerza H2 en memoria

## Documentación

La documentación del código se genera con Doxygen desde la raíz del
repositorio:

    doxygen Doxyfile

El HTML resultante queda en `docs/html` (abrir `docs/html/index.html`).
La fuente es `backend/src/main/java`, configurada en el `Doxyfile`.

El diagrama de arquitectura por módulos (`docs/diagramas/arquitectura_modulos.svg`)
se regenera desde su fuente PlantUML:

    java -jar plantuml.jar -tsvg docs/diagramas/arquitectura_modulos.puml
