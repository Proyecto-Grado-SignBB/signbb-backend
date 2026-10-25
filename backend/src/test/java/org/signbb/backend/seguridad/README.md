# Pruebas del módulo de seguridad {#pruebas_seguridad}

> Índice general de la suite: \ref pruebas.

Pruebas del paquete `org.signbb.backend.seguridad`: cuentas, roles, login con
JWT y la cadena de filtros que decide qué puede hacer cada rol. Los roles son
`PADRE` (se registra solo y hace recorridos), `MEDICO` (arma grafos y reglas) y
`MEDICO_SUPERVISOR` (además revisa lo de otros médicos y administra cuentas).

Las unitarias son JUnit 5 y Mockito, sin Spring, base de datos ni Redis. La de
integración (`ReglasAccesoIntegracionTest`) levanta la aplicación completa con
PostgreSQL y Redis, como el CI.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `servicios` | `ServicioCuentasImplTest` | 23 | registro de padres, login, alta de médicos por el supervisor y activar o desactivar cuentas; una contraseña de más de 72 bytes da 422; el padre guarda, cambia o borra el teléfono de su pediatra y uno mal escrito da 422; un correo inexistente también ejecuta BCrypt |
| `servicios` | `LimitadorIntentosTest` | 8 | límite por correo persistido en PostgreSQL: 5 fallos/15 min en ingreso, 3 intentos/1 h en registro, bloqueo vigente y vencido, clave hasheada y correo vacío |
| `servicios` | `ServicioTokensTest` | 7 | el JWT lleva `sub`, `rol` y `nombre`; uno vencido o firmado con otra clave se rechaza; un secreto corto no arranca |
| (raíz) | `InicializadorSeguridadTest` | 2 | el supervisor inicial se siembra una sola vez |
| (raíz) | `VerificadorSecretosProduccionTest` | 11 | en producción no se aceptan los valores de ejemplo, la documentación de la API activa ni el registro de SQL encendido; un entorno mal escrito impide arrancar |
| (raíz) | `ReglasAccesoIntegracionTest` | 20 | la cadena de filtros real: rutas públicas, `/actuator/health` abierto y el resto de Actuator sin exponer, 401 sin token, 403 por rol y dueño del recorrido; solo un padre guarda el teléfono del pediatra; un recorrido reiniciado o sin sesión da 410; el cuarto registro con el mismo correo da 429 |

En total son **71 pruebas** en estas clases. Las reglas de autor y de revisión de la autoría
(`GuardiaAutoriaTest`, `PoliticaAccesoBorradoresTest`, `ServicioRevisionImplTest`
y `ControladorRevisionTest`) están en \ref pruebas_autoria; la del dueño del
recorrido, en \ref pruebas_observaciones.

## Ayudas de prueba

- `UsuariosPrueba`: usuarios fijos (`MEDICO_A`, `MEDICO_B`, `SUPERVISOR`) y un
  resolvedor de `UsuarioActual` para los `standaloneSetup` de MockMvc, sin
  Spring Security.
- `ApiPrueba`: MockMvc con la cadena de seguridad real; ingresa, registra
  padres, crea cuentas clínicas con el supervisor sembrado y borra al final las
  cuentas que creó.

## Qué se prueba y por qué importa

### Cuentas (`ServicioCuentasImplTest`)
- **Registro de padres.** El correo se guarda en minúsculas y sin espacios y
  solo se guarda el hash BCrypt. Un correo repetido, un correo mal escrito o
  una contraseña de menos de 8 caracteres dan **422**.
- **Login.** Con los datos correctos devuelve el token. Si la contraseña está
  mal, el correo no existe o la cuenta está inactiva, responde **401 con el
  mismo mensaje**, para no revelar cuál de las tres falló. Un correo
  inexistente también ejecuta BCrypt con un hash ficticio para igualar el
  tiempo de respuesta.
- **Límite de intentos.** Los contadores viven en PostgreSQL, no en Redis. El
  ingreso se bloquea tras 5 fallos seguidos durante 15 minutos; el registro,
  tras 3 intentos en 1 hora por correo. Un ingreso correcto borra el contador
  de ingreso. El alcance actual no limita por IP.
- **Cuentas clínicas.** El supervisor crea médicos con su institución. Crear un
  `PADRE` (o sin rol) por esa vía da 422: los padres solo se registran solos.
  Un supervisor no puede desactivarse a sí mismo.

### Tokens (`ServicioTokensTest`)
El token se firma con HS256 y el resource server lo valida: se rechaza si
venció o si se firmó con otra clave. Un secreto de menos de 32 bytes impide
arrancar, para que nadie despliegue con una clave débil por descuido.

### Supervisor inicial (`InicializadorSeguridadTest`)
Si no hay ningún `MEDICO_SUPERVISOR`, se crea uno con la contraseña cifrada.
Si ya hay alguno, no hace nada, así que reiniciar el back no duplica cuentas.

### Secretos de producción (`VerificadorSecretosProduccionTest`)
Con `SIGNBB_ENTORNO=produccion`, la app no arranca si el secreto JWT, la
contraseña del supervisor inicial o las claves de Silo valen lo mismo que en
desarrollo, o si Swagger y `/v3/api-docs` siguen activos
(`SIGNBB_DOCS_ACTIVAS` distinto de `false`), o si el registro de SQL está
encendido (`SIGNBB_SHOW_SQL=true`), y el mensaje nombra las variables
sin mostrar los valores. En desarrollo se aceptan. Un entorno mal escrito también impide arrancar, y una
prueba comprueba que los valores de ejemplo del código sigan iguales a los de
`application.properties` y `application-docker.properties`, y otra que la
variable de la documentación esté conectada a springdoc y otra que el registro
de SQL esté apagado por defecto.

### Reglas de acceso (`ReglasAccesoIntegracionTest`)
Con la aplicación completa:

- sin token, la autoría, los recorridos, los usuarios y `/auth/yo` dan **401**
  con el mismo JSON `{mensaje, avisos}` que ya entiende el front;
- Swagger, `/media`, el login y el registro no piden token, y el *preflight*
  `OPTIONS` del front responde 200;
- `GET /actuator/health` responde 200 con `{"status":"UP"}` sin token y sin
  detalles; `/actuator/env` y `/actuator/beans` no responden 200 ni con el
  token del supervisor;
- un `PADRE` recibe **403** en la autoría y en los usuarios;
- un `MEDICO` recibe 403 en los recorridos, los usuarios, las revisiones,
  aprobar, rechazar y despublicar;
- un recorrido solo lo ve quien lo inició: otro padre recibe **404** al
  consultarlo, responderlo o retrocederlo, y no lo ve en "Mis recorridos";
- no se puede crear un `PADRE` por la API clínica;
- un token alterado da 401, y el token de una cuenta desactivada deja de servir.

## Cómo ejecutarlas

    ./mvnw test -Dtest='org.signbb.backend.seguridad.**'

`ReglasAccesoIntegracionTest` requiere PostgreSQL y Redis arriba
(`contenedores/compose.yaml`); las demás no.

La prueba manual, con `curl` y por las pantallas, está en
`pruebas_manuales/pruebas_seguridad.md`.
