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

## Documentación

La documentación del código se genera con Doxygen desde la raíz del
repositorio:

    doxygen Doxyfile

El HTML resultante queda en `docs/html` (abrir `docs/html/index.html`).
La fuente es `backend/src/main/java`, configurada en el `Doxyfile`.

El diagrama de arquitectura por módulos (`docs/diagramas/arquitectura_modulos.svg`)
se regenera desde su fuente PlantUML:

    java -jar plantuml.jar -tsvg docs/diagramas/arquitectura_modulos.puml
