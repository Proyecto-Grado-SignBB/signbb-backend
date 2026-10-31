# Pruebas del sistema {#pruebas}

Esta sección reúne la documentación de la **suite de pruebas del backend**
(Spring Boot 4.1.1, Java 25). Cubre los TADs clínicos, el motor de navegación,
el motor de reglas, la autoría (lienzo, reglas y revisión), la captura de
observaciones y la seguridad (cuentas, roles y JWT).

## Cómo ejecutarlas

Desde el directorio `backend`:

    ./mvnw test              # compila y ejecuta la suite completa
    ./mvnw verify            # build completo, igual que en CI (GitHub Actions)
    ./mvnw -Dtest=Clase test # una sola clase
    ./mvnw -Dtest=Clase#metodo test

Las pruebas unitarias son **JUnit 5 puro** y, cuando hace falta simular
colaboradores, **Mockito** (5.x, con el *inline mock maker*, de modo que también
se pueden simular clases `final`). No necesitan Spring, PostgreSQL ni Redis.

Las pruebas de integración usan `@SpringBootTest` o **servicios reales**
(PostgreSQL y Redis de `contenedores/compose.yaml`); el CI los levanta como
servicios efímeros (`signbb-backend/.github/workflows/build.yml`).

`AutoriaIntegracionTest` está **temporalmente deshabilitada** con `@Disabled`
mientras se revisa la suite unitaria por separado; sigue formando parte de la
documentación y se puede volver a activar quitando la anotación.

## Tipos de prueba

- **Unitarias (JUnit 5 ± Mockito):** no arrancan el contexto de Spring ni usan
  PostgreSQL/Redis reales. Incluyen las pruebas de TADs, servicios con
  colaboradores simulados, controladores con `MockMvc` en modo
  `standaloneSetup`, el validador de Jakarta y la lectura de JSON del
  classpath. Son la mayoría y corren en local sin contenedores.
- **Integración (`@SpringBootTest` o servicios reales):** arrancan la
  aplicación completa o hablan con PostgreSQL/Redis de verdad. Verifican
  persistencia (`jsonb`), cache (Redis), la cadena de seguridad y el recorrido
  de punta a punta. Se ejecutan en `./mvnw verify`.

`cache/ColaSeleccionMultipleRedisIntegracionTest` no usa Spring, pero sí un
Redis real: se clasifica como integración y se salta sola si Redis no responde.

## Pruebas unitarias

| Módulo | Pruebas | Tipo | Documentación |
|---|---:|---|---|
| Navegación: TADs, parsers, motor y servicio | 170 | JUnit 5 | \ref pruebas_navegacion "Ver página" |
| Reglas: TAD, evaluación, servicio y mapeo JSON | 23 | JUnit 5 + Mockito | \ref pruebas_reglas "Ver página" |
| Autoría: lienzo, subgrafos, reglas, autor y revisión | 179 | JUnit 5 + Mockito | \ref pruebas_autoria "Ver página" |
| Observaciones: captura "todo o nada", validación, dueño del recorrido y respuesta del cuidador | 56 | JUnit 5 + Mockito | \ref pruebas_observaciones "Ver página" |
| Seguridad, sin las pruebas de integración | 54 | JUnit 5 + Mockito | \ref pruebas_seguridad "Ver página" |
| Cache, sin las pruebas de integración | 40 | Mockito | \ref pruebas_cache "Ver página" |
| Mantenimiento: retención de versiones e imágenes | 16 | JUnit 5 + Mockito | — |
| Media: subida y referencias de imágenes | 16 | JUnit 5 + MockMvc | — |
| Errores generales: 400, 405, 409, 422 y 500 con cuerpo `{mensaje, avisos}` (`config/ManejadorErroresGeneralesTest`) | 8 | JUnit 5 + MockMvc | — |
| Validación de entradas: límites de largo y de caracteres de los DTO (`config/ValidacionEntradasTest`) | 4 | JUnit 5 + validador de Jakarta | — |
| Seed de demo: conversión de los JSON de `seed/demo/` | 2 | JUnit 5 | — |
| **Total unitarias** | **568** | | |

Las clases de integración de cada módulo se excluyen de esta tabla y se
listan en la sección siguiente.

## Pruebas de integración

| Clase | Pruebas | Infraestructura | Estado | Documentación |
|---|---:|---|---|---|
| `BackendApplicationTests` | 1 | PostgreSQL + Redis; `@SpringBootTest` | Activa | — |
| `PersistenciaEstructuraJsonTest` | 3 | PostgreSQL + Redis; `@SpringBootTest` | Activa | — |
| `AutoriaIntegracionTest` | 2 | PostgreSQL + Redis; `@SpringBootTest` | **Omitida (`@Disabled`)** | \ref pruebas_autoria "Ver página" |
| `CacheRedisIntegracionTest` | 4 | PostgreSQL + Redis real; `@SpringBootTest` | Activa; se salta si no hay cache | \ref pruebas_cache "Ver página" |
| `seguridad/ReglasAccesoIntegracionTest` | 20 | PostgreSQL + Redis; `@SpringBootTest` | Activa | \ref pruebas_seguridad "Ver página" |
| `cache/ColaSeleccionMultipleRedisIntegracionTest` | 5 | Redis real; sin Spring ni PostgreSQL | Activa; se salta si no hay Redis | \ref pruebas_cache "Ver página" |
| **Total integración** | **35** | | **33 activas + 2 omitidas** | |

`BackendApplicationTests` comprueba que el contexto carga y que las migraciones
de Flyway dejan el esquema que esperan las entidades. `PersistenciaEstructuraJsonTest`
hace ida y vuelta de los documentos JSON por la columna `estructura` (`jsonb`) y
verifica que `DatabaseInit` sembró el grafo raíz. `AutoriaIntegracionTest` arma
un subgrafo con el servicio, lo publica y lo recorre como la cuidadora.

## Totales

| Tipo | Presentes | Ejecutadas | Omitidas |
|---|---:|---:|---:|
| Unitarias | 568 | 568 | 0 |
| Integración | 35 | 33 | 2 |
| **Total** | **603** | **601** | **2** |

## Resultados

La ejecución local del 31-oct-2026 sobre `develop` corre **603 pruebas: 601
pasan y 2 se omiten** (`AutoriaIntegracionTest`, deshabilitada). El CI ejecuta
`./mvnw -B verify` con PostgreSQL y Redis como servicios.

> Nota: para leer reportes frescos, ejecutar `./mvnw clean` antes: `target/surefire-reports`
> puede conservar reportes de clases que ya no existen.
