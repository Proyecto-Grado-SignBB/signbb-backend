# Plan de implementación SignBB

*Actualizado Oct 24, 2026 · @Someone*

El plan va ordenado por importancia, porque no hay fecha fija. El esfuerzo es una estimación mía; lo marcado con (*) es una sugerencia que se puede quitar, y «Fase N» es una fase del plan de backend ya guardado.

## Reglas de trabajo

| Regla | Qué significa |
|---|---|
| No compilo ni corro el backend | Dani ejecuta `./mvnw test` después de cada fase. |
| No hago commit ni push | Entrego los `git add <archivo>` exactos, nunca `git add ..` |
| Módulo de reglas | Solo se toca en las pruebas y el Javadoc. |

## Etapa 1. Seguridad y despliegue

| # | Qué es | Para qué sirve | Prioridad | Esfuerzo | Del plan |
|---|---|---|---|---|---|
| 1.1 | Secretos por variables de entorno | Quitar del código el secreto JWT, `Supervisor123` y `silo-admin/ejemplo123`, y que la app no arranque en producción con valores de desarrollo. Así nadie puede falsificar tokens ni entrar con claves públicas. | Crítica | Pequeño | Fase 2 |
| 1.2 | Cerrar puertos y Swagger | Cerrar hacia afuera Postgres, Redis, Silo y Adminer, y proteger la documentación de la API. Evita acceso directo a los datos. | Crítica | Pequeño | Fase 2 (*) |
| 1.3 | Migraciones con Flyway | Pasar de `ddl-auto=update` a `validate` y crear la migración V1. Los cambios en la base de datos quedan controlados antes de tener datos reales. Debe ir antes de cualquier cambio de tablas. | Alta | Pequeño o mediano | Fase 1 |
| 1.4 | Backend en contenedor | Dockerfile y servicio en el compose, esperando a Postgres, Redis y Silo. Todo arranca con un solo comando y el sistema se recupera solo si cae (AD-07). | Alta | Mediano | Fase 2 |
| 1.5 | Chequeo de salud | Actuator con `/actuator/health` sin sesión. Permite que un monitor externo pregunte si el sistema está vivo (AD-01 a AD-04). | Alta | Pequeño | Fase 2 |
| 1.6 | Endurecer el arranque | Apagar `show-sql` y dejar `DemoTriajeRunner` y `DatabaseInit` solo en desarrollo, para no meter datos de demostración en producción. | Media | Pequeño | Fase 2 (*) |
| 1.7 | HTTPS con Caddy | Certificado automático y dominio configurable. Sin HTTPS la app no se instala en el celular y los datos viajan sin cifrar. Incluye cambiar `https://TU-DOMINIO-AQUI/api` en el front y revisar CORS. | Alta | Mediano | Fase 3 (*) |
| 1.8 | Respaldo y restauración | Copia diaria de Postgres, Silo y Redis con retención, y un script para restaurar (AC-04). Protege contra pérdida de datos. | Alta | Mediano | Fase 4 |

## Etapa 2. Seguridad clínica y consistencia de datos

*Esta etapa no está en el plan aprobado. Propongo agregarla.*

| # | Qué es | Para qué sirve | Prioridad | Esfuerzo |
|---|---|---|---|---|
| 2.1 | Consistencia al guardar una respuesta (AC-01) | Hoy Redis y Postgres no comparten transacción: un doble clic o una caída puede dejar el recorrido avanzado sin observación, o duplicarla. Hay que decidir el orden o una compensación. | Crítica | Grande |
| 2.2 | Validación de entradas (AS-03) | Poner límites de largo y de caracteres por campo, en el servidor y en las pantallas. Corrige el error 500 de `POST /respuestas` vacío. | Crítica | Mediano |
| 2.3 | Manejador de errores genérico | Que todo error tenga cuerpo y un código correcto (400, 409, 422), en lugar de 500 sin explicación. | Alta | Pequeño |
| 2.4 | Botones de emergencia | «Buscar ayuda ahora» debe abrir la llamada al 123 y las urgencias cercanas, y «Contactar a mi pediatra» debe hacer algo real. Hoy solo vuelven al inicio. | Crítica | Mediano |
| 2.5 | Límite de 10 preguntas | Hoy cierra con una recomendación tranquila. Hay que decidir cómo debe cerrar para no dejar un caso sin atender. | Alta | Pequeño (tras decidir) |
| 2.6 | Camino causal y razones legibles | Mostrar al padre por qué salió ese resultado, con texto claro y no con identificadores. | Media | Pequeño o mediano |
| 2.7 | Reanudar y reiniciar (CU10 y CU11) | Mensaje claro cuando vence la sesión de 8 h, estado coherente en Postgres, y botón para abandonar o reiniciar la consulta. | Alta | Mediano |

## Etapa 3. Funcionalidad que falta

| # | Qué es | Para qué sirve | Prioridad | Esfuerzo |
|---|---|---|---|---|
| 3.1 | Historial de versiones y restaurar | Ver las versiones publicadas y volver a una anterior. Hoy despublicar borra la fila publicada y es difícil deshacer. | Media | Grande |
| 3.2 | Publicar las reglas del grafo raíz | Hoy esas reglas solo se publican junto con un subgrafo. Permite actualizar las reglas que combinan varias categorías. | Media | Mediano |
| 3.3 | Imágenes | Borrar el archivo de S3 al quitar una imagen, quitar el EXIF y decidir si las imágenes de borradores deben ser públicas. | Media | Mediano |
| 3.4 | Validar el grafo compuesto | Hoy los ciclos y nodos huérfanos solo dejan un aviso en el registro. Que bloqueen. | Media | Pequeño |
| 3.5 | Pantallas de consulta | Página 404, «Reintentar» que reenvíe la respuesta, «Continuar» deshabilitado sin selección y `alt` en las imágenes. | Media | Pequeño |
| 3.6 | Mensaje al despublicar el único subgrafo | El servidor lo rechaza con 422. Que la pantalla lo explique. | Baja | Pequeño |

## Etapa 4. Supervisor, cuentas y auditoría

| # | Qué es | Para qué sirve | Prioridad | Esfuerzo |
|---|---|---|---|---|
| 4.1 | Borrar la carpeta auditoria | Son siete archivos `package-info` creados por error de nombre. Se quita con `git rm -r` y se ajusta una línea del README de observaciones. | Baja | Pequeño |
| 4.2 | Registro de autoaprobación y despublicación | Guardar quién publicó sin revisión o despublicó, y cuándo. Requiere una columna nueva, por lo que va como migración V2. | Media | Mediano |
| 4.3 | Cuentas: límites y enumeración | Límite de login que no dependa solo del correo y que no desaparezca si Redis cae, límite al registro, y mismo mensaje y tiempo cuando el correo existe y cuando no. | Media | Mediano |
| 4.4 | Supervisor que envía a revisión un subgrafo propio | Hoy enviar no revisa el rol y podría quedar atascado. Está en `ServicioRevisionImpl`, que está en la lista de archivos que no se tocan. | Baja | Pequeño |

## Etapa 5. Pruebas y calidad

| # | Qué es | Para qué sirve | Prioridad | Esfuerzo | Del plan |
|---|---|---|---|---|---|
| 5.1 | Pruebas AD-01 a AD-04 | Una prueba por cada criterio de disponibilidad y recuperación. Falta el texto de esas tablas. | Alta | Mediano | Fase 5 |
| 5.2 | Refuerzo de pruebas de reglas | Cubrir el motor y los casos límite de `ReglaClinica` y `BaseReglas`. | Media | Mediano | Fase 5 |
| 5.3 | Base de datos y Redis aparte para pruebas | Hoy una prueba de integración borra versiones de la base de desarrollo. | Media | Pequeño o mediano | — |
| 5.4 | CI del front y pruebas de extremo a extremo | Que el front se pruebe solo en cada cambio y que un recorrido completo se pruebe con Playwright. | Media | Grande | — |
| 5.5 | Javadoc | Subir la cobertura al 90 %, sin cambiar lógica. | Media | Mediano | Fase 6 |
| 5.6 | Medir la concurrencia | `ContextoClinico` es `synchronized`. Medir con carga antes de cambiar nada. | Media | Mediano | — |
| 5.7 | Fronteras entre módulos (ArchUnit) | Congelar las importaciones cruzadas actuales para que no aparezcan más. | Baja | Mediano | Fase 7 (*) |

## Etapa 6. Limpieza

| # | Qué es | Para qué sirve | Prioridad | Esfuerzo |
|---|---|---|---|---|
| 6.1 | .gitattributes | Fijar el fin de línea. Hoy 9 archivos aparecen modificados solo por saltos de línea y eso puede causar conflictos grandes. | Alta | Pequeño |
| 6.2 | Limpieza general | Sacar `docs/html` del repositorio, quitar archivos sueltos y código muerto, mover funciones repetidas del front y cerrar ramas viejas. | Baja | Mediano |

## Orden sugerido

| Ronda | Qué hacer | Por qué |
|---|---|---|
| 1 | 6.1, 1.1, 1.2, 4.1 | Rápidos y quitan riesgos o ruido. |
| 2 | 2.2, 2.3, 2.1 | Validación y consistencia, lo más delicado del backend. |
| 3 | 2.4, 2.5, 2.6, 2.7 | Cerrar el flujo clínico del padre. |
| 4 | 1.3, 1.4, 1.5, 1.6 | Flyway primero, luego contenedor y arranque. |
| 5 | 1.7, 1.8 | HTTPS y respaldos, ya con todo en contenedor. |
| 6 | 4.2, 4.3, 3.1 a 3.6 | Funcionalidad y cuentas. |
| 7 | 5.1 a 5.7 y 6.2 | Pruebas, documentación y limpieza. |

## Para versión futura

Solo se documentan y no se construyen: cambio y recuperación de contraseña, cierre de sesión que invalide el token en el servidor, cifrado en reposo, valor de entrada, fotos y sensores. Falta decidir si entran en esta entrega el historial del bebé (CU05) y el uso sin conexión.

## Decisiones pendientes

| Ítem | Decisión que necesito de Dani |
|---|---|
| 2.5 | ¿El límite de 10 preguntas debe cerrar como «consultar médico» o se queda como recomendación? |
| 2.4 | ¿«Urgencias cercanas» es un enlace a un mapa o necesita geolocalización? |
| 3.3 | ¿Las imágenes de borradores pueden ser públicas por UUID o deben requerir sesión? |
| 4.2 | ¿Se registra la autoaprobación y la despublicación en esta entrega? |
| 4.4 | ¿Ya se puede tocar `ServicioRevisionImpl`, o sigue en la lista de archivos que no se tocan? |
| 5.1 | ¿Me pasas el texto de AD-01 a AD-04? |
| 1.7 | ¿Ya hay un dominio para HTTPS? |