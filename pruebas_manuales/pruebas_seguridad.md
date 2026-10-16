# Pruebas de seguridad: cuentas, roles, autor y revisión

Plan de prueba manual del módulo de seguridad (rama `seguridad` del back y
`seguridad-front` del front). Tiene dos partes:

- **Parte A, por API (S-01 a S-17):** cada caso trae sus comandos `curl`, el
  código HTTP esperado y, cuando aplica, la verificación en la base.
- **Parte B, por las pantallas (U-01 a U-17):** cada caso trae los pasos y lo
  que se debe ver. Lo del supervisor (aprobar, rechazar, crear médicos,
  desactivar cuentas) no tiene pantallas: se hace con los comandos de la parte A
  o con Swagger.

Las pruebas automáticas del mismo módulo están en
`backend/src/test/java/org/signbb/backend/seguridad/README.md`.

## Requisitos

- Java 25, Docker Desktop encendido y Git.
- `curl` y `jq`. En macOS: `brew install jq`.
- Para la parte B, además Node.js con npm.
- Los comandos son de **bash**. En macOS la terminal abre zsh: escribe `bash`
  antes de pegar la preparación.

## Preparar la máquina

Se parte de una base **vacía**: el estado nuevo `EN_REVISION` no cabe en el
*check constraint* que Hibernate creó para la base vieja, y los borradores de
antes no tienen autor.

```bash
cd signbb-backend
git fetch origin && git checkout seguridad && git pull

cd contenedores
docker compose down -v      # borra los datos: la base se vuelve a sembrar
docker compose up -d --wait

cd ../backend
./mvnw spring-boot:run      # deja esta terminal abierta hasta ver "Started BackendApplication"
```

En el log del arranque debe salir `Se creó el supervisor inicial
supervisor@signbb.local. Cambia su contraseña…`.

### Otra máquina con Windows

- Usa **Git Bash** (viene con Git para Windows y trae `curl`) para pegar los
  bloques de este documento tal cual. Instala `jq` con `winget install jqlang.jq`
  y abre Git Bash de nuevo.
- En PowerShell, el back se arranca con `.\mvnw.cmd spring-boot:run`, y `curl`
  es un alias de `Invoke-WebRequest`: usa `curl.exe` o, mejor, Git Bash.
- Si no puedes instalar `jq`, haz los casos con Swagger (ver al final).

## Preparación general

Pega este bloque en una terminal bash. Deja definidas las variables y funciones
que usan todos los casos, en la misma terminal.

```bash
BASE=http://localhost:8080/api
SUP=supervisor@signbb.local
SUP_CLAVE=Supervisor123
CLAVE=clave-de-prueba-2026

# api <token o -> <método> <ruta> [json]: imprime el cuerpo y en la última línea "HTTP <código>".
api(){
  local t="$1" m="$2" r="$3" d="${4-}"
  local args=(-s -X "$m" -H 'Content-Type: application/json' -w '\nHTTP %{http_code}\n')
  [ "$t" != - ] && args+=(-H "Authorization: Bearer $t")
  [ -n "$d" ] && args+=(-d "$d")
  curl "${args[@]}" "$BASE$r"
}
# json ...: igual que api, pero solo el cuerpo (para pasarlo a jq).
json(){ api "$@" | sed '$d'; }
# login <correo> <clave>: imprime el token.
login(){ json - POST /auth/login "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" | jq -r .token; }
# psql: consultas a la base del compose.
psql(){ docker exec -i contenedores-db-1 psql -U admin -d signbb "$@"; }
```

Comprobación: `api - GET /autoria/categorias` debe terminar en `HTTP 401`.

---

# Parte A: por API

## S-01: sin token la API responde 401

```bash
api - GET /autoria/subgrafos
api - POST /recorridos
```

**Esperado:** las dos terminan en `HTTP 401` con
`{"mensaje":"…","avisos":[]}`.

## S-02: el supervisor sembrado ingresa

```bash
T_SUP=$(login "$SUP" "$SUP_CLAVE"); echo "$T_SUP" | cut -c1-20
api "$T_SUP" GET /auth/yo
```

**Esperado:** se imprime el inicio de un token (no `null`). `/auth/yo` responde
`HTTP 200` con `"rol":"MEDICO_SUPERVISOR"`.

## S-03: credenciales malas

```bash
api - POST /auth/login "{\"correo\":\"$SUP\",\"contrasena\":\"otra-clave\"}"
api - POST /auth/login '{"correo":"nadie@signbb.local","contrasena":"otra-clave"}'
```

**Esperado:** las dos dan `HTTP 401` con **el mismo mensaje**, para no revelar
si el correo existe.

## S-04: el supervisor crea los médicos A y B; un médico no puede

```bash
api "$T_SUP" POST /usuarios "{\"nombre\":\"Dra. Ana Médica\",\"correo\":\"a@signbb.local\",\"contrasena\":\"$CLAVE\",\"rol\":\"MEDICO\",\"institucion\":\"Fundación Canguro\"}"
api "$T_SUP" POST /usuarios "{\"nombre\":\"Dr. Beto Médico\",\"correo\":\"b@signbb.local\",\"contrasena\":\"$CLAVE\",\"rol\":\"MEDICO\",\"institucion\":\"Hospital Prueba\"}"
T_A=$(login a@signbb.local "$CLAVE"); T_B=$(login b@signbb.local "$CLAVE")
api "$T_A" POST /usuarios "{\"nombre\":\"Intruso\",\"correo\":\"x@signbb.local\",\"contrasena\":\"$CLAVE\",\"rol\":\"MEDICO\"}"
api "$T_SUP" POST /usuarios "{\"nombre\":\"Padre\",\"correo\":\"x@signbb.local\",\"contrasena\":\"$CLAVE\",\"rol\":\"PADRE\"}"
```

**Esperado:** `HTTP 201` y `HTTP 201`; el médico A recibe `HTTP 403`; crear un
`PADRE` por esta vía da `HTTP 422`.

## S-05: registro de padres

```bash
api - POST /auth/registro "{\"nombre\":\"Papá Uno\",\"correo\":\"P1@SignBB.local\",\"contrasena\":\"$CLAVE\"}"
api - POST /auth/registro "{\"nombre\":\"Repetido\",\"correo\":\"p1@signbb.local\",\"contrasena\":\"$CLAVE\"}"
api - POST /auth/registro '{"nombre":"Corta","correo":"corta@signbb.local","contrasena":"1234"}'
api - POST /auth/registro "{\"nombre\":\"Mamá Dos\",\"correo\":\"p2@signbb.local\",\"contrasena\":\"$CLAVE\"}"
T_P1=$(login p1@signbb.local "$CLAVE"); T_P2=$(login p2@signbb.local "$CLAVE")
psql -c "select id, correo, rol, left(hash_contrasena, 4) as hash from usuario order by id;"
```

**Esperado:** `HTTP 201` (con token y `"rol":"PADRE"`), `HTTP 422` por el correo
repetido (aunque cambien las mayúsculas), `HTTP 422` por la clave corta y
`HTTP 201`. En la base, el correo de P1 quedó en minúsculas y el hash empieza
con `$2a$` (BCrypt), nunca la contraseña.

## S-06: roles cruzados

```bash
api "$T_P1" GET /autoria/subgrafos
api "$T_A" POST /recorridos
api "$T_A" GET /usuarios
```

**Esperado:** `HTTP 403` en los tres.

## S-07: el autor sale de la cuenta, no del request

```bash
PIEL=$(json "$T_A" GET /autoria/categorias | jq -r '.[]|select(.puenteId|test("piel"))|.puenteId')
S=$(json "$T_A" POST /autoria/subgrafos "{\"nombre\":\"Ictericia QA\",\"puenteId\":\"$PIEL\",\"etiquetaOpcion\":\"Piel u ojos amarillos\",\"autor\":\"Otro\",\"institucion\":\"Otra\"}")
echo "$S" | jq '{id, autor, institucion, autorId, estado}'
ID=$(echo "$S" | jq -r .id)
psql -c "select id, autor, autor_id from borrador_subgrafo_entity where id=$ID;"
```

**Esperado:** `autor` es «Dra. Ana Médica» e `institucion` «Fundación
Canguro», no «Otro»/«Otra». En la base, `autor_id` es el id de A (ver S-05).

## S-08: otro médico no ve ni toca lo de A

```bash
json "$T_B" GET /autoria/subgrafos | jq length
api "$T_B" GET /autoria/subgrafos/$ID
api "$T_B" PUT /autoria/subgrafos/$ID "{\"nombre\":\"Robado\",\"puenteId\":\"$PIEL\",\"etiquetaOpcion\":\"Robado\"}"
json "$T_SUP" GET /autoria/subgrafos | jq length
api "$T_SUP" PUT /autoria/subgrafos/$ID "{\"nombre\":\"Cambio\",\"puenteId\":\"$PIEL\",\"etiquetaOpcion\":\"Cambio\"}"
```

**Esperado:** B ve `0` subgrafos y recibe `HTTP 403` al abrir y al editar. El
supervisor ve `1`, pero tampoco edita lo de otro: `HTTP 403`.

## S-09: A arma la primera pregunta y la base de reglas

```bash
R=$(json "$T_A" POST /autoria/subgrafos/$ID/nodos '{"tipo":"PREGUNTA","texto":"¿Desde cuándo se ve amarillo?","variasRespuestas":false,"respuestas":[{"texto":"Desde hoy","gravedad":"MODERADA"},{"texto":"Hace más de 2 días","gravedad":"GRAVE"}]}')
P=$(echo "$R" | jq -r '.nodos[0].id'); R1=$(echo "$R" | jq -r '.nodos[0].respuestas[0].id'); R2=$(echo "$R" | jq -r '.nodos[0].respuestas[1].id')
GRAFO=$(echo "$R" | jq -r .grafoId)
BASE_ID=$(json "$T_A" POST /autoria/bases "{\"nombre\":\"Reglas ictericia QA\",\"descripcion\":\"QA\",\"grafoId\":\"$GRAFO\"}" | jq -r .id)
api "$T_B" POST /autoria/bases "{\"nombre\":\"Ajena\",\"descripcion\":\"QA\",\"grafoId\":\"$GRAFO\"}" | tail -1
psql -c "select id, grafo_id, autor_id from borrador_base_regla_entity;"
```

**Esperado:** la base se crea a nombre de A (`autor_id` = A). B no puede crear
una base para el subgrafo de A: `HTTP 403`.

## S-10: enviar a revisión con avisos y luego completo

```bash
api "$T_A" POST /autoria/subgrafos/$ID/enviar-revision
json "$T_A" POST /autoria/subgrafos/$ID/nodos "{\"despuesDe\":{\"nodoId\":\"$P\",\"respuestaId\":\"$R1\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Consulte al médico hoy.\",\"desenlace\":\"CONSULTA_MEDICA\"}" >/dev/null
C=$(json "$T_A" POST /autoria/subgrafos/$ID/nodos "{\"despuesDe\":{\"nodoId\":\"$P\",\"respuestaId\":\"$R2\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Lleve al bebé a urgencias.\",\"desenlace\":\"URGENCIA\"}" | jq -r '.nodos[]|select(.desenlace=="URGENCIA")|.id')
api "$T_A" POST /autoria/bases/$BASE_ID/reglas "{\"nombre\":\"R1\",\"descripcion\":\"Más de 2 días es urgencia\",\"casos\":[[{\"idNodo\":\"$P\",\"respuesta\":\"Hace más de 2 días\"}]],\"nivel\":\"URGENCIA\",\"nodoDestino\":\"$C\"}" | tail -1
api "$T_A" POST /autoria/subgrafos/$ID/enviar-revision | tail -1
json "$T_A" GET /autoria/subgrafos/$ID | jq .estado
```

**Esperado:** el primer envío da `HTTP 422` con `avisos` bloqueantes (las
respuestas aún no tienen camino). Con las conclusiones y la regla, el segundo da
`HTTP 200` y el estado queda `"EN_REVISION"`.

## S-11: en revisión no se edita, y publicar ya no existe

```bash
api "$T_A" PUT /autoria/subgrafos/$ID "{\"nombre\":\"Cambio\",\"puenteId\":\"$PIEL\",\"etiquetaOpcion\":\"Cambio\"}"
api "$T_A" POST /autoria/bases/$BASE_ID/reglas "{\"nombre\":\"R2\",\"descripcion\":\"x\",\"casos\":[[{\"idNodo\":\"$P\",\"respuesta\":\"Desde hoy\"}]],\"nivel\":\"URGENCIA\",\"nodoDestino\":\"$C\"}" | tail -1
api "$T_A" POST /autoria/subgrafos/$ID/publicar | tail -1
```

**Esperado:** `HTTP 422` con «Está en revisión; retíralo para editarlo.», `HTTP
422` en las reglas, y `HTTP 404` o `HTTP 405` en `publicar`.

## S-12: el autor no aprueba; rechazar exige comentario

```bash
api "$T_A" POST /autoria/subgrafos/$ID/aprobar | tail -1
json "$T_SUP" GET /autoria/revisiones | jq '.[]|{id, nombre, autor}'
api "$T_SUP" POST /autoria/subgrafos/$ID/rechazar | tail -1
api "$T_SUP" POST /autoria/subgrafos/$ID/rechazar '{"comentario":"Agrega qué hacer si el bebé no come."}' | sed '$d' | jq '{estado, comentarioRevision, revisadoEn}'
psql -c "select estado, revisor_id, comentario_revision from borrador_subgrafo_entity where id=$ID;"
```

**Esperado:** A recibe `HTTP 403`. El supervisor ve el subgrafo en sus
revisiones. Rechazar sin cuerpo da `HTTP 422`; con comentario, el subgrafo
vuelve a `"BORRADOR"` con el comentario, y `revisor_id` es el supervisor.

## S-13: reenviar y aprobar publica

```bash
api "$T_A" POST /autoria/subgrafos/$ID/enviar-revision | tail -1
json "$T_A" GET /autoria/subgrafos/$ID | jq .comentarioRevision
api "$T_SUP" POST /autoria/subgrafos/$ID/aprobar | sed '$d' | jq '{estado, publicadoEn, revisadoEn}'
psql -c "select estado, revisor_id from borrador_subgrafo_entity where id=$ID;"
psql -c "select estado from borrador_base_regla_entity where id=$BASE_ID;"
```

**Esperado:** al reenviar se borra el comentario anterior (`null`). Aprobar da
`HTTP 200` con `"PUBLICADO"`; `revisor_id` es el supervisor y la base de reglas
también queda `PUBLICADO`.

## S-14: el padre recorre y el recorrido queda a su nombre

```bash
REC=$(json "$T_P1" POST /recorridos); echo "$REC" | jq '.pregunta.opciones'
RID=$(echo "$REC" | jq -r .recorridoId)
OP=$(echo "$REC" | jq -r '.pregunta.opciones[]|select(test("piel";"i"))' | head -1)
json "$T_P1" POST /recorridos/$RID/respuestas "{\"respuesta\":\"$OP\"}" | jq '.pregunta.opciones'
json "$T_P1" POST /recorridos/$RID/respuestas '{"respuesta":"Piel u ojos amarillos"}' | jq '.pregunta.texto'
json "$T_P1" POST /recorridos/$RID/respuestas '{"respuesta":"Hace más de 2 días"}' | jq '{estado, nivel: .resultado.nivel, mensaje: .resultado.mensaje}'
psql -c "select id, usuario_id, estado, nivel from recorrido;"
psql -c "select distinct usuario_id from observacion_entity where recorrido_id='$RID';"
```

**Esperado:** en Piel aparece la opción «Piel u ojos amarillos», luego la
pregunta del subgrafo, y el recorrido termina `FINALIZADO` con nivel
`URGENCIA`. En la base, `recorrido.usuario_id` y las observaciones son de P1.

## S-15: otro padre no ve el recorrido de P1

```bash
api "$T_P2" GET /recorridos/$RID | tail -1
api "$T_P2" POST /recorridos/$RID/retroceder | tail -1
api "$T_P2" POST /recorridos/$RID/respuestas '{"respuesta":"Desde hoy"}' | tail -1
json "$T_P2" GET /recorridos | jq length
json "$T_P1" GET /recorridos | jq '.[]|{recorridoId, estado, nivel}'
```

**Esperado:** `HTTP 404` en los tres (no se revela que existe). P2 tiene `0`
recorridos; P1 tiene 1, `FINALIZADO` con `URGENCIA`.

## S-16: desactivar una cuenta

```bash
B_ID=$(json "$T_SUP" GET /usuarios | jq -r '.[]|select(.correo=="b@signbb.local")|.id')
api "$T_SUP" PUT /usuarios/$B_ID/activo '{"activo":false}' | tail -1
api - POST /auth/login "{\"correo\":\"b@signbb.local\",\"contrasena\":\"$CLAVE\"}" | tail -1
api "$T_B" GET /autoria/subgrafos | tail -1
SUP_ID=$(json "$T_SUP" GET /auth/yo | jq -r .id)
api "$T_SUP" PUT /usuarios/$SUP_ID/activo '{"activo":false}' | tail -1
```

**Esperado:** `HTTP 200`; B ya no ingresa (`HTTP 401`, el mismo mensaje de
S-03) y su token viejo deja de servir (`HTTP 401`). El supervisor no puede
desactivarse a sí mismo: `HTTP 422`.

## S-17: token alterado

```bash
MALO="${T_A%?}x"; [ "$MALO" = "$T_A" ] && MALO="${T_A%?}y"
api "$MALO" GET /autoria/subgrafos | tail -1
```

**Esperado:** `HTTP 401`.

## Limpieza

Para repetir el plan desde cero, vuelve a crear la base:

```bash
cd signbb-backend/contenedores && docker compose down -v && docker compose up -d --wait
```

y reinicia el back (Ctrl+C y `./mvnw spring-boot:run`) para que vuelva a sembrar
el grafo raíz y el supervisor.

## Alternativa con Swagger

1. Abre `http://localhost:8080/swagger-ui.html`.
2. En **Sesión → POST /api/auth/login**, pulsa *Try it out* y envía
   `{"correo":"supervisor@signbb.local","contrasena":"Supervisor123"}`.
3. Copia el `token` de la respuesta, pulsa **Authorize** (arriba a la derecha),
   pégalo y confirma.
4. Desde ahí, cada operación va con ese token. Para actuar como otra cuenta,
   ingresa con ella y vuelve a pulsar *Authorize*.

Cada caso S-NN se puede hacer igual con los mismos cuerpos JSON. La
verificación en la base se hace con Adminer (`http://localhost:8180`, servidor
`db`, usuario `admin`) o con `psql`.

---

# Parte B: por las pantallas

## Preparar el front

En otra terminal, con el back arriba (sección «Preparar la máquina»):

```bash
cd signbb-frontend
git fetch origin && git checkout seguridad-front && git pull
npm install
npx ng build ui-compartida      # la librería compartida va antes que las apps
npx ng serve ui-autoria         # http://localhost:4300 (médicos)
```

En una tercera terminal:

```bash
cd signbb-frontend
npx ng serve ui-consulta        # http://localhost:4200 (familias)
```

Las dos apps guardan su sesión por separado: ingresar en una no abre la otra.
Las cuentas de médico se crean con S-04 (o con Swagger).

| Caso | Pasos | Se debe ver |
|---|---|---|
| U-01 | Abre `http://localhost:4300` sin sesión. Prueba también `http://localhost:4300/perfil` | La pantalla de ingreso; cualquier ruta lleva a ella |
| U-02 | Ingresa con una contraseña mala | El error dentro de la pantalla, sin alertas del navegador |
| U-03 | Crea los médicos A y B con S-04. Ingresa como A | La barra lateral muestra el nombre y la institución reales de A y el tablero está vacío |
| U-04 | Pulsa «Cerrar sesión» y luego el botón atrás del navegador | Vuelve al ingreso; atrás no deja volver al tablero |
| U-05 | Como A, crea «Ictericia en la piel» (categoría Piel, «Piel u ojos amarillos»), arma una pregunta con dos conclusiones y una regla | El subgrafo queda en el tablero de A; el perfil muestra el nombre y la institución de A y sus conteos |
| U-06 | Cierra sesión e ingresa como B | B no ve el subgrafo de A |
| U-07 | Como A, deja un "+" sin camino y pulsa «Enviar a revisión»; complétalo y envíalo | Primero, el aviso bloqueante; después, la etiqueta «En revisión» y el lienzo en solo lectura, con «Retirar de revisión» |
| U-08 | Con curl (S-12), el supervisor rechaza el subgrafo con un comentario | — |
| U-09 | A recarga el subgrafo | Un aviso arriba con el comentario del supervisor; puede editar y volver a enviar. No hay botón «Despublicar» |
| U-10 | A lo reenvía. Con curl (S-13), el supervisor lo aprueba. A recarga | «Publicado» |
| U-11 | Abre `http://localhost:4200` y pulsa «Comenzar» | Lleva al ingreso; desde ahí «Crear cuenta» y registra a P1 |
| U-12 | Como P1, recorre Piel → «Piel u ojos amarillos» hasta la conclusión | Aparece la opción nueva y se muestra el resultado |
| U-13 | Como P1, abre «Mis recorridos» | El recorrido aparece como terminado y «Ver resultado» lo abre |
| U-14 | Copia la URL del resultado, cierra sesión, registra a P2 y pega la URL | «Ese recorrido no está disponible» |
| U-15 | En `:4200` ingresa con la cuenta de A; en `:4300`, con la de P1 | Las dos se rechazan, cada una con su mensaje (usar la otra app) |
| U-16 | Con curl (S-16), el supervisor desactiva a B. B intenta ingresar en `:4300` | B no puede entrar |
| U-17 | En `:4300`, borra la sesión en DevTools → Application → Local Storage y recarga | Vuelve a la pantalla de ingreso |
