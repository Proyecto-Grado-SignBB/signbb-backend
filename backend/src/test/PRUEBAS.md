# Pruebas del sistema {#pruebas}

Esta sección reúne la documentación de la **suite de pruebas del backend**
(Spring Boot 4.1.1, Java 25). Cubre los TADs clínicos, el motor de navegación,
el motor de reglas, la autoría (lienzo y reglas) y la captura de observaciones.

## Cómo ejecutarlas

Desde el directorio `backend`:

    ./mvnw test              # compila y ejecuta la suite completa
    ./mvnw verify            # build completo, igual que en CI (GitHub Actions)
    ./mvnw -Dtest=Clase test # una sola clase
    ./mvnw -Dtest=Clase#metodo test

Las pruebas unitarias son **JUnit 5 puro** y, cuando hace falta simular
colaboradores, **Mockito** (5.x, con el *inline mock maker*, de modo que también
se pueden simular clases `final`). No necesitan Spring, PostgreSQL ni Redis.

Las pruebas de integración usan `@SpringBootTest` y **requieren PostgreSQL y
Redis** arriba (`contenedores/compose.yaml`); el CI los levanta como servicios
efímeros (`signbb-backend/.github/workflows/build.yml`).

## Tipos de prueba

- **Unitarias (JUnit 5 ± Mockito):** no arrancan el contexto de Spring. Son la
  mayoría y corren en local sin contenedores.
- **Integración (`@SpringBootTest`):** arrancan la aplicación completa y
  verifican persistencia (`jsonb`), cache (Redis) y el recorrido de punta a
  punta. Se ejecutan en `./mvnw verify`.

## Suite por módulo

| Módulo | Pruebas | Tipo | Documentación |
|---|---:|---|---|
| Navegación: TADs, parsers, motor y servicio | 171 | JUnit 5 | \ref pruebas_navegacion "Ver página" |
| Reglas: TAD, evaluación, servicio y mapeo JSON | 28 | JUnit 5 + Mockito | \ref pruebas_reglas "Ver página" |
| Autoría: lienzo, subgrafos y reglas | 63 + 11 | JUnit 5 + Mockito | \ref pruebas_autoria "Ver página" |
| Observaciones: captura y validación de respuestas | 10 | JUnit 5 + Mockito | \ref pruebas_observaciones "Ver página" |
| Cache: contenido clínico y sesiones en Redis | — | Mockito / `@SpringBootTest` | \ref pruebas_cache "Ver página" |

## Pruebas de integración de la raíz

En `backend/src/test/java/org/signbb/backend/` viven las pruebas que arrancan el
contexto completo:

- `BackendApplicationTests` — *smoke test*: el contexto de la aplicación carga.
- `PersistenciaEstructuraJsonTest` — ida y vuelta de los documentos JSON por la
  columna `estructura` (`jsonb`) y verificación de la siembra de `DatabaseInit`.
- `AutoriaIntegracionTest` — de punta a punta: armar un subgrafo con el servicio,
  publicarlo y recorrerlo como la cuidadora (ver \ref pruebas_autoria).
- `CacheRedisIntegracionTest` — cache-aside con Redis real; se salta sola si el
  servicio `cache` no responde (ver \ref pruebas_cache).

## Resultados en CI

El workflow `build` corre `./mvnw -B verify` con PostgreSQL y Redis como
servicios. Las corridas y los conteos por clase se detallan en las páginas de
cada módulo.

> Nota: para leer reportes frescos, ejecutar `./mvnw clean` antes: `target/surefire-reports`
> puede conservar reportes de clases que ya no existen.
