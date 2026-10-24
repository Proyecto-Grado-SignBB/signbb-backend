# Pruebas del cache {#pruebas_cache}

> Índice general de la suite: \ref pruebas.

Este README documenta las **pruebas del cache Redis** (contenido clínico y
sesiones de los recorridos). Las pruebas del módulo de navegación (TADs,
parsers, motor y servicio) están documentadas en
\ref pruebas_navegacion. Las del módulo de autoría (lienzo, subgrafos y
reglas) están en \ref pruebas_autoria.

## Ubicación

- Unitarias (paquete `org.signbb.backend.cache`, sin Redis real):
  `ResolvedorContenidoClinicoTest`, `RepositorioSesionRedisTest` y
  `CandadoRecorridoRedisTest`.
- Integración (perfil `docker`): `org.signbb.backend.CacheRedisIntegracionTest`.
- Limpieza programada de versiones e imágenes (paquete
  `org.signbb.backend.mantenimiento`, todas unitarias): ver la sección
  «Limpieza programada» al final.

## ResolvedorContenidoClinicoTest — resolución cache-aside del contenido

Con el template de Redis y los repositorios simulados (Mockito):

- **Hit de Redis**: el documento se toma del cache y la base de datos no se
  consulta ni una vez.
- **Miss**: se lee la fila de la base de datos y se puebla el cache con TTL
  de 24 horas (`signbb:contenido:v2:grafo` / `signbb:contenido:v2:reglas`).
- **Redis caído al leer**: se degrada a la base de datos sin fallar (Redis
  nunca es dependencia dura).
- **Redis caído al escribir**: la resolución igual retorna el documento;
  solo se pierde la copia caliente.
- **Base sin contenido**: `IllegalStateException` explícito — el classpath
  NO es un mecanismo de fallback en runtime, solo la semilla de
  `DatabaseInit`.
- **`invalidar()`**: borra las claves de contenido (el hook del futuro
  módulo de Autoría).

## RepositorioSesionRedisTest — sesiones en Redis

Con un template simulado respaldado por un mapa en memoria y el contenido
real (grafo y reglas de `static/grafo_sistema/`):

- **Round-trip EN_CURSO**: crear y buscar restaura respuestas acumuladas y
  la pregunta pendiente (reconstruida por id contra el grafo vigente).
- **Round-trip FINALIZADO**: guardar y buscar restaura nivel, destino,
  razones (por id de regla), camino causal y puntaje.
- **Sesión inexistente**: 404 (`RecursoNoEncontradoException`).
- **Redis caído al buscar o guardar**: `CacheNoDisponibleException`
  (el `ControllerAdvice` la responde como 503; nunca degrada a memoria para
  evitar sesiones que aparecen y desaparecen).
- **`existe` y `eliminar`** (CU10/CU11): `existe` dice si la clave sigue en
  Redis (una sesión vencida a las 8 h ya no está) y `eliminar` borra la del
  recorrido reiniciado; con Redis caído también dan `CacheNoDisponibleException`.

## CandadoRecorridoRedisTest — candado por recorrido

Con un template simulado. El candado (`signbb:candado:recorrido:<id>`) deja
pasar una operación a la vez sobre el mismo recorrido (responder, retroceder):

- **Toma, ejecuta y suelta**: se toma con tiempo límite de 10 s y se suelta
  con su propia ficha (un candado ajeno nunca se borra).
- **Ya tomado**: lanza `RecorridoOcupadoException` (409), no ejecuta la acción
  ni suelta el candado de otro.
- **La acción falla**: el candado se suelta igual.
- **Redis caído al tomarlo**: `CacheNoDisponibleException` (503) y no se ejecuta
  la acción.
- **Redis falla al soltarlo**: no tapa el resultado; el candado caduca solo.

## CacheRedisIntegracionTest — integración

Requiere `contenedores/compose.yaml` levantado (db + cache). Las tres
primeras se **saltan solas** (suposición `modoCacheActivo`: bean de sesiones
Redis + Redis respondiendo) cuando el despliegue no está en modo cache —
entonces aparecen como *skipped*, no como falla:

- **La primera resolución puebla el cache**: tras invalidar, resolver el
  grafo deja la clave en Redis verificable.
- **Sirve del cache aunque se borre la fila de la base**: la transacción de
  la prueba borra la fila y la resolución siguiente sale del cache; el
  rollback restaura la fila.
- **La sesión sobrevive un reinicio simulado**: una instancia nueva del
  repositorio (como tras un arranque limpio) recupera la sesión completa.
- **El contexto sirve contenido completo** (sin requisito de Redis): si el
  cache no responde, resuelve directo desde la base, lo que verifica el
  camino de degradación de punta a punta.

## ColaSeleccionMultipleRedisIntegracionTest — cola de la selección múltiple

Usa un Redis real (servicio `cache` del compose, `localhost:6379`; se cambia
con `-Dsignbb.test.redis.host` y `-Dsignbb.test.redis.port`) y un grafo
pequeño de dos ramas armado en código (`q1` de selección múltiple con `qa`,
peso 3, y `qb`, peso 2), de modo que la cola queda con una recomendación
pendiente. No necesita Spring ni la base: se **salta sola** si Redis no
responde. Cada paso se guarda y se relee con una instancia nueva del
repositorio (reinicio simulado):

- **La cola pendiente queda en Redis y sobrevive al reinicio**: se sigue `qa`,
  `qb` queda en la cola, la ruta y las respuestas se recuperan y la clave
  tiene TTL de hasta 24 h.
- **Tras la primera recomendación se retoma la opción pendiente** y al final
  salen ambas recomendaciones, cada una con su camino causal.
- **Retroceder tras recargar** restaura la cola y descarta la recomendación
  del paso deshecho.
- **Una alerta en una rama** cierra con la alerta sola y vacía la cola
  guardada, también tras retroceder y cambiar de rama.

## Cómo ejecutarlas

Requieren PostgreSQL y Redis arriba (`contenedores/compose.yaml`):

    ./mvnw test                                          # suite completa (contenedores arriba)
    ./mvnw test -Dtest=ResolvedorContenidoClinicoTest,RepositorioSesionRedisTest,CacheRedisIntegracionTest,ColaSeleccionMultipleRedisIntegracionTest

## Notas

- Claves en Redis: `signbb:contenido:v2:grafo|reglas` (TTL 24 h) y
  `signbb:sesion:<id>` (TTL 24 h); inspección manual con
  `docker exec -it contenedores-cache-1 redis-cli`
  (ver `contenedores/MANUAL_CONTENEDORES.md`).
- El CI (GitHub Actions) corre `verify` con servicios PostgreSQL y Redis
  efímeros (`build.yml`), de modo que la suite completa se ejecuta ahí.

## Limpieza programada (`mantenimiento`)

Una vez al día (3:30, `signbb.limpieza.cron`) el backend borra por sí solo las
versiones viejas del contenido y las imágenes sin uso. No hay endpoint. Todas
las pruebas son unitarias, con Mockito y sin Spring, base de datos, Redis ni
Silo.

- **`PoliticaRetencionVersionesTest`**: con 20 versiones o menos no se borra
  nada; con más se borran las más antiguas; nunca la vigente ni las 20 más
  recientes; una versión reemplazada hace menos de 25 h se conserva (justo en
  el límite se conserva y un segundo después se borra); no importa el orden en
  que lleguen.
- **`LimpiadorVersionesTest`**: borra de la base y después de Redis
  (`ResolvedorVersiones.olvidar`) y de la memoria (`ContextoClinico.olvidar`);
  si no hay nada que borrar no toca nada.
- **`LimpiadorImagenesTest`**: borra solo la imagen que no usa ningún lienzo,
  ninguna copia publicada, ningún grafo ni ninguna versión conservada; nunca
  las subidas hace menos de 24 h ni archivos que no tengan nombre de imagen
  del servidor; si no hay grafos en la base, si Silo no responde al listar o si
  falla la lectura de un documento no borra nada; si falla borrar una sigue con
  las demás.
- **`LimpiezaProgramadaTest`**: primero las versiones y después las imágenes;
  si una parte falla la otra se ejecuta igual y el error no se propaga.
- **`ResolvedorVersionesTest`** (`olvidar`) y **`ContextoClinicoTest`**
  (`olvidar`): borran solo las claves y las versiones indicadas, y un fallo de
  Redis no rompe nada.
- **`ServicioImagenesTest`** (`nombresEn`): encuentra los nombres de imagen
  dentro de cualquier texto JSON.

`AlmacenImagenesS3.listar` y `eliminar` hablan con Silo y se prueban a mano
(ver `docs/autoria-subgrafos.md`).
