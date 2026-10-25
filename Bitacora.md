# Bitácora de cambios SignBB

*Actualizada Oct 25, 2026 · @Someone*

## Estado actual

Rondas 1 a 6 cerradas. Quedaron descartados 1.8, 3.1 y 4.2; 3.6 se decide en reunión y siguen pendientes 3.5, 4.4 y toda la Etapa 5. Backend en verde (660 en la última corrida) y frontend en verde. Siguiente: Etapa 5 (pruebas y calidad).

## Cambios hechos

| Paso | Qué se hizo | Pruebas |
|---|---|---|
| Punto de partida | Se detuvo el PostgreSQL de Windows que ocupaba el puerto 5432; sin cambios de código. | 562 en verde |
| 6.1 .gitattributes | Fin de línea fijo para todo el equipo. | 562 en verde |
| 1.1 Secretos | Con `SIGNBB_ENTORNO=produccion` la app no arranca si quedan claves de ejemplo. | 568 en verde |
| 1.2 Puertos y Swagger | Puertos solo en `127.0.0.1`; `SIGNBB_DOCS_ACTIVAS=false` apaga Swagger y es obligatorio en producción. | 570 en verde |
| 4.1 Carpeta auditoria | Se borraron 7 archivos `package-info.java` vacíos (nombre equivocado, era *autoria*) y se ajustó un README. | 570 en verde |
| 2.3 Manejador de errores | Clase nueva `ManejadorErroresGenerales`: todo error sin manejar responde con `{mensaje, avisos}` y el código correcto (400, 405, 409, 500). El 500 no muestra texto interno y queda en el registro. | 577 en verde |
| 2.2a Validación de entradas | Dependencia `spring-boot-starter-validation` y límites de largo y de caracteres en los DTO (nombres 100, correo 254, texto de pregunta 1000, opción 200, contraseña 72, etc.). Si se pasan: 422 con mensaje. La respuesta vacía del cuidador ahora da 400 y no 500. Contraseña de más de 72 bytes: 422. | 591 en verde |
| 2.2b Límites en las pantallas | `maxlength` en los campos de `ui-autoria` y `ui-consulta` con los mismos límites del servidor (incluye el comentario de rechazo, 2000). Los botones de agregar respuesta (10), caso (20) y condición (20) se desactivan al llegar al máximo. | Frontend en verde (tipos y pruebas) |
| 2.1a Consistencia al guardar | Candado por recorrido en Redis (una operación a la vez; si no, 409). Responder y retroceder guardan la sesión, la observación y el recorrido como todo o nada: si algo falla, Postgres revierte y Redis vuelve a como estaba. **Riesgo aceptado:** caída del servidor justo entre Redis y el cierre de la transacción. | en verde (607 esperadas) |
| 2.1b Pregunta esperada | El frontend manda el id de la pregunta que contesta; si ya no es la actual, el servidor responde 409 sin registrar nada y la pantalla muestra la pregunta actual. Sin el id funciona como antes. Con esto queda cerrado el 2.1. | backend y frontend en verde |
| Ronda 3 · 2.4 Teléfono del pediatra | Campo `telefono_pediatra` en la cuenta y `PUT /api/auth/yo/pediatra` (solo familia; vacío lo borra, inválido da 422). En la app, «Buscar ayuda ahora» llama al 123 y «Contactar a mi pediatra» guía qué contarle y marca el número guardado; «Urgencias cercanas» queda como tarjeta «Próximamente». | en verde |
| Ronda 3 · 2.5 Límite de 10 preguntas | Se mantiene el cierre en nivel `NORMAL`, con la razón escrita en `MotorNavegacion.LIMITE_PREGUNTAS` y en el README de navegación. Queda revisable con el asesor clínico. | en verde |
| Ronda 3 · 2.6 Camino causal | `PasoCausalDTO` trae el texto de cada nodo y las razones van como nombres de regla. La pantalla «¿Por qué este resultado?» muestra el camino con la respuesta dada. | en verde |
| Ronda 3 · 2.7 Reanudar y reiniciar | Estados `VENCIDO` y `ABANDONADO`; un recorrido sin sesión en Redis responde 410 al consultar, responder o retroceder; `POST /api/recorridos/{id}/reiniciar` lo abandona y empieza otro (409 si ya terminó). El front avisa del 410 y permite reiniciar con confirmación, también desde la primera pregunta. | en verde |
| Ronda 4 · 1.3 Flyway | `V1__esquema_inicial.sql` y `V2__telefono_pediatra.sql`, `ddl-auto=validate` y baseline en 1 para bases previas. | en verde |
| Ronda 4 · 1.4 Backend en contenedor | `backend/Dockerfile` multi-etapa con usuario sin root y servicio `backend` en el compose: espera a que Postgres, Redis y Silo estén sanos, `restart: always` y variables de entorno. El CI construye la imagen. | en verde |
| Ronda 4 · 1.5 Chequeo de salud | Actuator expone solo `/actuator/health`, sin sesión y sin detalles. | en verde |
| Ronda 4 · 1.6 Arranque | `show-sql` sale de las propiedades; la siembra y la demo quedan tras `SIGNBB_SIEMBRA_ACTIVA` y `SIGNBB_DEMO_ACTIVA` (apagadas por defecto). | en verde |
| Fuera del plan · integridad de datos | `V3__integridad_e_indices.sql`: llaves foráneas hacia usuario y recorrido, dominios de `recorrido.estado/nivel`, PK de `observacion_opciones` e índices de las consultas de los DAO. Pool de Hikari fijo de 10 y el CI falla si aparece SQL nativo. | en verde |
| Ronda 5 · 1.7 HTTPS con Caddy | **Implementado.** `signbb-frontend/deploy/` con Dockerfile y Caddy: sirve la PWA por HTTPS, hace proxy de `/api` y `/media` (mismo origen, sin CORS), conserva los volúmenes del certificado y documenta la CA interna para Android, iOS, Linux, macOS y Windows. `apiUrl` pasa a `/api` relativo y `index.html` gana metadatos PWA de iOS. En el backend, `server.forward-headers-strategy=framework` para reconocer https y host detrás del proxy. | backend y frontend en verde |
| Ronda 5 · 1.8 Respaldo y restauración | **No se implementa:** el respaldo se considera innecesario para el alcance del proyecto. | — |
| Ronda 6 · 4.3 Cuentas | Contadores en PostgreSQL (`V4__limite_intentos.sql`, entidad y repositorio con upsert atómico): 5 fallos/15 min en ingreso y 3 intentos/1 h en registro, limitados a la cuenta. Se retira el limitador en Redis, así que una caída de Redis ya no desactiva el límite. Ingreso en tiempo constante con un hash BCrypt ficticio y hash calculado antes de comprobar el correo repetido. Limpieza diaria de contadores vencidos. | en verde |
| Ronda 6 · 3.2 Reglas del grafo raíz | Se bloquea toda interacción con la base de reglas del grafo raíz: listar, crear, leer, catálogo, editar, descartar, eliminar y publicar, con el refuerzo en `GuardiaAutoria`, `ServicioAutoriaReglasImpl` y `PublicadorRegla`. El editor del front ya no ofrece esa categoría. | backend y frontend en verde |
| Ronda 6 · 3.3 Imágenes | Al quitar una imagen del nodo o del borrador se borra de Silo después del commit, y solo si ninguna pregunta ni versión la referencia. Al subir, se aplica la orientación EXIF y se vuelve a codificar sin metadatos (GIF animado rechazado, WebP convertido a PNG o JPEG). `/media/{nombre}` entrega solo imágenes publicadas; el editor usa `/api/autoria/imagenes/{nombre}`, que pide sesión. | backend y frontend en verde |
| Ronda 6 · 3.4 Grafo compuesto | Un nodo suelto pasa de aviso a bloqueante: impide enviar a revisión, publicar directo y aprobar. Se actualizaron los Javadocs y `docs/autoria-subgrafos.md`. | backend y frontend en verde |
| Ronda 6 · 4.2 Registro de aprobación | **No se implementa:** con un único médico supervisor en las pruebas, el registro de aprobación y despublicación tendría pocos usos. | — |
| Ronda 6 · 3.1 Historial de versiones | **Fuera de alcance:** solo se manejan los estados borrador y publicado; no hay historial ni restauración. | — |
| Ronda 6 · 3.5 Pantallas de consulta | **Pendiente:** 404, «Reintentar» que reenvíe, «Continuar» deshabilitado sin selección y `alt` en las imágenes. Hoy no hay ruta comodín y los `alt` de las imágenes están vacíos. | — |
| Ronda 6 · 3.6 Mensaje al despublicar | **A decidir en reunión:** dejar la categoría deshabilitada cuando no queden subgrafos, en vez de solo el 422 del servidor. | — |
| Ronda 6 · limpieza | Se quitó el código de compatibilidad con la sesión vieja (`EstadoPaciente` y el motor) y la doble versión al publicar: ahora una publicación registra una sola versión y las reglas se publican junto con su subgrafo. | backend y frontend en verde |

## Estado por ronda

| Ronda | Ítems | Estado | Commits |
|---|---|---|---|
| 1 | 6.1, 1.1, 1.2, 4.1 | completos | `7dc77b0` |
| 2 | 2.2, 2.3, 2.1 | completos | `c0cb2fb` y front `29d5a55` |
| 3 | 2.4, 2.5, 2.6, 2.7 | completos; «Urgencias cercanas» queda como «Próximamente» (decisión de 2.4 abierta) | `b539404`, `caa7f4e`, `9ae2c64` y front `aa58bef` |
| 4 | 1.3, 1.4, 1.5, 1.6 | completos | `5377a73` |
| 4 | integridad referencial, índices, pool y regla anti-SQL-nativo en CI | fuera del plan, hecho | `533b09e` |
| 5 | 1.7; 1.8 | 1.7 hecho; 1.8 no se implementa | `6194f07` y front `25fc7f1` |
| 6 | 4.3, 3.2, 3.3, 3.4 | hechos | `64732fe`, `0f30ff3`, `2c4a8d6`, `32dff49`, `685bc4a`, `049de99` y front `aa45977`, `22137d4`, `e617de1` |
| 6 | 4.2, 3.1, 3.5, 3.6 | 4.2 descartado, 3.1 fuera de alcance, 3.5 pendiente y 3.6 a decidir en reunión | — |
| 6 | limpieza de sesión vieja y de publicación | hecho | `f708d59`, `0043210` y front `6a99764` |

## Descartado en esta entrega

| Ítem | Razón |
|---|---|
| 1.8 Respaldo y restauración | Innecesario para el alcance del proyecto. |
| 3.1 Historial de versiones | Solo se manejan borrador y publicado. |
| 4.2 Registro de autoaprobación y despublicación | Un único médico supervisor de prueba: pocos usos. |

## Variables para producción

> Si alguna de estas falla al arrancar, la app no inicia y el mensaje dice cuál corregir.

| Variable | Valor en producción |
|---|---|
| `SIGNBB_ENTORNO` | `produccion` |
| `SIGNBB_JWT_SECRETO` | propio, de 32 bytes o más |
| `SIGNBB_SUPERVISOR_CONTRASENA` | propia, no `Supervisor123` |
| `SIGNBB_MEDIA_ACCESS_KEY` y `SIGNBB_MEDIA_SECRET_KEY` | propias, no las de ejemplo de Silo |
| `SIGNBB_DOCS_ACTIVAS` | `false` |

## Hallazgos y decisiones

- El PostgreSQL de Windows (`postgresql-x64-18`) puede volver a arrancar al reiniciar el computador y chocar con el del contenedor.
- El 1.1 son los secretos; Flyway es el 1.3.
- Swagger no usa login porque no puede mandar el token; se apaga en producción y queda `docs/api/openapi.yaml`.
- Las claves de la base de datos y de Silo en `compose.yaml` se resuelven con el 1.4.
- Dos ramas crearon un `V3` a la vez (integridad y límite de intentos); el de límites se renumeró a `V4`. Regla: antes de crear una migración, comprobar el último número que haya en el remoto.
- El CI prohíbe SQL nativo, con una excepción documentada: el upsert atómico de `RepositorioLimiteIntentos`, porque hacerlo con JPA exigiría locks y reintentos.
- El 1.7 usa la CA interna de Caddy porque no hay certificado público para una IP privada. Para pasar a dominio público basta quitar `tls internal` y usar DNS-01.
- El 3.3 cerró la decisión que estaba abierta: `/media` solo entrega contenido publicado y los borradores se ven por un endpoint con sesión.
- El 3.4 cambia el comportamiento visible: un nodo suelto ya bloquea la publicación en vez de solo avisar.
- El 4.4 sigue sin tocar y el supervisor hoy publica directo, así que el caso de «enviar a revisión un subgrafo propio» puede haber dejado de aplicar.

## Pendiente

- [ ] 3.5: 404, «Reintentar», «Continuar» deshabilitado sin selección y `alt` en las imágenes
- [ ] 3.6: decidir en reunión si la categoría se deshabilita cuando no quedan subgrafos
- [ ] 4.4: rol al enviar a revisión un subgrafo propio (pendiente de decisión)
- [ ] Etapa 5 (5.1 a 5.7) y 6.2
- [ ] 2.4: definir si «Urgencias cercanas» es un mapa o necesita geolocalización
- [ ] Pendiente de 2.2: el comentario de rechazo (`RechazoRequest`) no se valida porque está en `ControladorRevision`

## Decisiones abiertas

| Ítem | Decisión |
|---|---|
| 2.4 | ¿«Urgencias cercanas» es un enlace a un mapa o necesita geolocalización? |
| 3.6 | ¿Se deshabilita la categoría cuando no hay subgrafos o se deja el 422? |
| 4.4 | ¿Se puede tocar `ServicioRevisionImpl` o sigue en la lista de archivos que no se tocan? |
| 5.1 | Falta el texto de AD-01 a AD-04. |

Ya cerradas: 1.7 (CA interna de Caddy), 2.5 (se queda en `NORMAL`, revisable con el asesor) y 3.3 (`/media` solo publicadas). Descartadas: 1.8, 3.1 y 4.2.
