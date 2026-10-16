# Pruebas de Versiones — scripts de apoyo (bash)

Comandos de preparación y verificación para la hoja **Pruebas-Versiones** de
`Plan_pruebas_navegacion_SignBB.ods`. Las **interacciones de cada prueba se
hacen con la interfaz** (panel de autoría y app de la cuidadora); este documento
solo prepara el contenido abstracto por API y ayuda a verificar el estado en
base de datos.

El contenido de prueba es **abstracto** (sin información clínica):

- **QA A**: lienzo «Pregunta 1 → A1/A2/A3» + base de reglas con una regla.
- **QA B**: el mismo lienzo corto, sin base de reglas.
- **QA L**: cadena larga «Paso 1 → Paso 2 → … → Paso 12», sin base de reglas,
  para pasar el umbral de 10 preguntas.

## Requisitos

- Backend arriba en `http://localhost:8080` (perfil `docker`).
- PostgreSQL y Redis de `signbb-backend/contenedores/compose.yaml`:
  `docker compose up -d --wait`.
- `curl`, `jq` y `docker` instalados.
- En la rama `seguridad`, la API pide token: la preparación ingresa con el
  supervisor sembrado, crea un médico QA (autor del contenido) y un padre QA.
  Ya no se publica directo: `publicar` envía a revisión y el supervisor aprueba.
  En `seguridad-front` el médico no tiene botón «Despublicar»: donde la hoja
  diga despublicar por interfaz, usa
  `sup -X POST "$BASE/autoria/subgrafos/$ID/despublicar"`.

## Preparación general

Crea (de forma idempotente) el contenido abstracto en tres categorías del grafo
raíz. Se prefieren «piel»/«temperatura»/«respiracion» si existen; si no, las tres
primeras disponibles.

```bash
set -euo pipefail
BASE=http://localhost:8080/api
DB=contenedores-db-1
psql(){ docker exec -i "$DB" psql -U admin -d signbb "$@"; }
# Cuentas (rama seguridad): el contenido QA es del médico QA y lo publica el
# supervisor al aprobarlo. Las cuentas se crean la primera vez; si ya existen,
# la API responde 422 y se sigue.
SUP_CORREO=supervisor@signbb.local; SUP_CLAVE=Supervisor123; QA_CLAVE=clave-qa-2026
token(){ curl -s -H 'Content-Type: application/json' -X POST "$BASE/auth/login" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" | jq -r .token; }
TOK_SUP=$(token "$SUP_CORREO" "$SUP_CLAVE")
curl -s -o /dev/null -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_SUP" -X POST "$BASE/usuarios" -d "{\"nombre\":\"Médico QA\",\"correo\":\"qa.medico@signbb.local\",\"contrasena\":\"$QA_CLAVE\",\"rol\":\"MEDICO\",\"institucion\":\"QA\"}"
curl -s -o /dev/null -H 'Content-Type: application/json' -X POST "$BASE/auth/registro" -d "{\"nombre\":\"Padre QA\",\"correo\":\"qa.padre@signbb.local\",\"contrasena\":\"$QA_CLAVE\"}"
TOK_MED=$(token qa.medico@signbb.local "$QA_CLAVE"); TOK_PAD=$(token qa.padre@signbb.local "$QA_CLAVE")
api(){ curl -s -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_MED" "$@"; }    # médico QA
sup(){ curl -s -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_SUP" "$@"; }    # supervisor
padre(){ curl -s -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_PAD" "$@"; } # padre QA
# publicar <id>: el médico lo envía a revisión y el supervisor lo aprueba (publica subgrafo y reglas).
publicar(){ api -X POST "$BASE/autoria/subgrafos/$1/enviar-revision" >/dev/null; sup -X POST "$BASE/autoria/subgrafos/$1/aprobar"; }

# Borra QA A/QA B/QA L y sus bases publicadas o en borrador (repetible).
limpiar_qa(){
  for gid in grafo_qa_a grafo_qa_b grafo_qa_l; do
    local id estado
    id=$(api "$BASE/autoria/subgrafos" | jq -r ".[]|select(.grafoId==\"$gid\")|.id")
    [ -z "$id" ] && continue
    api -X POST "$BASE/autoria/subgrafos/$id/retirar-revision" >/dev/null  # por si quedó en revisión
    estado=$(api "$BASE/autoria/subgrafos/$id" | jq -r .estado)
    if [ "$estado" != BORRADOR ]; then
      sup -X POST "$BASE/autoria/subgrafos/$id/despublicar" >/dev/null
    fi
    api -X DELETE "$BASE/autoria/subgrafos/$id" >/dev/null
  done
  for bid in $(api "$BASE/autoria/bases" | jq -r '.[]|select(.grafoId|startswith("grafo_qa"))|.id'); do
    api -X DELETE "$BASE/autoria/bases/$bid" >/dev/null
  done
}

# Lienzo corto: Pregunta 1 -> A1(GRAVE)=urgente, A2(MODERADA)=consulta, A3(LEVE)=casa.
# $1 = id del borrador; imprime "P1 RA1 RA2 RA3".
poblar(){
  local id="$1" r p1 ra1 ra2 ra3
  r=$(api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d '{"tipo":"PREGUNTA","texto":"Pregunta 1","variasRespuestas":false,"respuestas":[{"texto":"A1","gravedad":"GRAVE"},{"texto":"A2","gravedad":"MODERADA"},{"texto":"A3","gravedad":"LEVE"}]}')
  p1=$(echo "$r"|jq -r '.nodos[0].id')
  ra1=$(echo "$r"|jq -r '.nodos[0].respuestas[0].id')
  ra2=$(echo "$r"|jq -r '.nodos[0].respuestas[1].id')
  ra3=$(echo "$r"|jq -r '.nodos[0].respuestas[2].id')
  api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d "{\"despuesDe\":{\"nodoId\":\"$p1\",\"respuestaId\":\"$ra1\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Resultado urgente\",\"desenlace\":\"URGENCIA\"}" >/dev/null
  api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d "{\"despuesDe\":{\"nodoId\":\"$p1\",\"respuestaId\":\"$ra2\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Resultado consulta\",\"desenlace\":\"CONSULTA_MEDICA\"}" >/dev/null
  api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d "{\"despuesDe\":{\"nodoId\":\"$p1\",\"respuestaId\":\"$ra3\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Resultado casa\",\"desenlace\":\"RECOMENDACION\"}" >/dev/null
  echo "$p1 $ra1 $ra2 $ra3"
}

# Cadena larga sin reglas: Paso 1..Paso N; cada pregunta tiene "Seguir" (siguiente
# pregunta) y "Parar" (conclusión). Para P-01: elige "Seguir" hasta el límite.
# $1 = id del borrador; $2 = N (número de preguntas).
poblar_larga(){
  local id="$1" n="$2" k q seg par r
  r=$(api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d '{"tipo":"PREGUNTA","texto":"Paso 1","variasRespuestas":false,"respuestas":[{"texto":"Seguir","gravedad":"LEVE"},{"texto":"Parar","gravedad":"LEVE"}]}')
  q=$(echo "$r"|jq -r '.nodos[-1].id')
  seg=$(echo "$r"|jq -r '.nodos[-1].respuestas[0].id')
  par=$(echo "$r"|jq -r '.nodos[-1].respuestas[1].id')
  for k in $(seq 2 "$n"); do
    api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d "{\"despuesDe\":{\"nodoId\":\"$q\",\"respuestaId\":\"$par\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Fin $((k-1))\",\"desenlace\":\"RECOMENDACION\"}" >/dev/null
    r=$(api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d "{\"despuesDe\":{\"nodoId\":\"$q\",\"respuestaId\":\"$seg\"},\"tipo\":\"PREGUNTA\",\"texto\":\"Paso $k\",\"variasRespuestas\":false,\"respuestas\":[{\"texto\":\"Seguir\",\"gravedad\":\"LEVE\"},{\"texto\":\"Parar\",\"gravedad\":\"LEVE\"}]}")
    q=$(echo "$r"|jq -r '.nodos[-1].id')
    seg=$(echo "$r"|jq -r '.nodos[-1].respuestas[0].id')
    par=$(echo "$r"|jq -r '.nodos[-1].respuestas[1].id')
  done
  api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d "{\"despuesDe\":{\"nodoId\":\"$q\",\"respuestaId\":\"$par\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Fin $n\",\"desenlace\":\"RECOMENDACION\"}" >/dev/null
  api -X POST "$BASE/autoria/subgrafos/$id/nodos" -d "{\"despuesDe\":{\"nodoId\":\"$q\",\"respuestaId\":\"$seg\"},\"tipo\":\"CONCLUSION\",\"texto\":\"Fin final\",\"desenlace\":\"RECOMENDACION\"}" >/dev/null
}

limpiar_qa

# Tres categorías distintas (prefiere piel/temperatura/respiracion si existen).
mapfile -t CATS < <(api "$BASE/autoria/categorias" | jq -r '.[].puenteId')
usado=()
elegir(){
  local pref="$1" c
  RESULT=""
  for c in "${CATS[@]}"; do
    case " ${usado[*]-} " in *" $c "*) continue;; esac
    if [ "$c" = "$pref" ]; then RESULT="$c"; break; fi
  done
  if [ -z "$RESULT" ]; then
    for c in "${CATS[@]}"; do
      case " ${usado[*]-} " in *" $c "*) continue;; esac
      RESULT="$c"; break
    done
  fi
  usado+=("$RESULT")
}
elegir puente_piel;        PUE_A="$RESULT"
elegir puente_temperatura; PUE_B="$RESULT"
elegir puente_respiracion; PUE_L="$RESULT"
if [ -z "$PUE_A" ] || [ -z "$PUE_B" ] || [ -z "$PUE_L" ]; then
  echo "Hacen falta al menos 3 categorías en el grafo raíz." >&2; exit 1
fi
echo "Categorías: QA A=$PUE_A  QA B=$PUE_B  QA L=$PUE_L"

# QA A + base QA R.
A=$(api -X POST "$BASE/autoria/subgrafos" -d "{\"nombre\":\"QA A\",\"puenteId\":\"$PUE_A\",\"etiquetaOpcion\":\"QA A\",\"autor\":\"QA\",\"institucion\":\"QA\"}"|jq -r .id)
read P1 RA1 RA2 RA3 < <(poblar "$A")
GRAF_A=$(api "$BASE/autoria/subgrafos/$A"|jq -r .grafoId)
C_CON=$(api "$BASE/autoria/subgrafos/$A"|jq -r '.nodos[]|select(.texto=="Resultado consulta")|.id')
R=$(api -X POST "$BASE/autoria/bases" -d "{\"nombre\":\"QA R\",\"descripcion\":\"Reglas QA\",\"grafoId\":\"$GRAF_A\",\"autor\":\"QA\",\"institucion\":\"QA\"}"|jq -r .id)
api -X POST "$BASE/autoria/bases/$R/reglas" -d "{\"nombre\":\"R1\",\"descripcion\":\"A2 lleva a consulta\",\"casos\":[[{\"idNodo\":\"$P1\",\"respuesta\":\"A2\"}]],\"nivel\":\"CONSULTAR_MEDICO\",\"nodoDestino\":\"$C_CON\"}" | jq -e '[.avisos[]|select(.bloqueante)]|length==0'
publicar "$A" | jq -e '.estado=="PUBLICADO"' >/dev/null   # publica QA A con su base QA R

# QA B (corto) y QA L (cadena larga), sin base de reglas.
Bs=$(api -X POST "$BASE/autoria/subgrafos" -d "{\"nombre\":\"QA B\",\"puenteId\":\"$PUE_B\",\"etiquetaOpcion\":\"QA B\",\"autor\":\"QA\",\"institucion\":\"QA\"}"|jq -r .id)
poblar "$Bs" >/dev/null
publicar "$Bs" >/dev/null
L=$(api -X POST "$BASE/autoria/subgrafos" -d "{\"nombre\":\"QA L\",\"puenteId\":\"$PUE_L\",\"etiquetaOpcion\":\"QA L\",\"autor\":\"QA\",\"institucion\":\"QA\"}"|jq -r .id)
poblar_larga "$L" 12
publicar "$L" | jq -e '.estado=="PUBLICADO"' >/dev/null

echo "Preparación lista: QA A id=$A grafo=$GRAF_A base=$R | QA B id=$Bs | QA L id=$L (12 pasos)"
```

Comprobación rápida (deben salir `1 1 1 1 0`: los tres grafos, la base de QA A y
la ausencia de base en QA L):

```bash
psql -At -c "select (select count(*) from grafo_clinico_entity where estructura->'graph'->>'id'='grafo_qa_a'),
                    (select count(*) from grafo_clinico_entity where estructura->'graph'->>'id'='grafo_qa_b'),
                    (select count(*) from grafo_clinico_entity where estructura->'graph'->>'id'='grafo_qa_l'),
                    (select count(*) from base_reglas_entity where estructura->>'grafoId'='grafo_qa_a'),
                    (select count(*) from base_reglas_entity where estructura->>'grafoId'='grafo_qa_l');"
```

## Comandos por prueba

Las secciones siguientes asumen que ejecutaste la **Preparación general** en la
misma terminal (deja definidos `BASE`, `DB`, `api` y `psql`).

## P-01 — Completar la navegación en estado normal por pasar el umbral de preguntas

Control de navegación normal usando la cadena larga **QA L** (12 preguntas, sin
reglas). Pasos por interfaz: ver la hoja **Pruebas-Versiones**, P-01. No requiere
comandos: basta con el contenido del script de preparación.

Verificación informativa (la versión vigente no cambia):

```bash
psql -At -c "select max(id) as version_vigente from version_contenido;"
```

## P-02 — Interrumpir la navegación por reglas

Pasos por interfaz: ver la hoja, P-02. La regla **R1** de «QA R» corta el
recorrido al responder «A2». Verifica que la base está publicada:

```bash
psql -At -c "select count(*) from base_reglas_entity where estructura->>'grafoId'='grafo_qa_a';"  # 1
```

## P-03 — Publicar una nueva versión de un grafo clínico usado en una sesión activa

Pasos por interfaz: ver la hoja, P-03. Para crear una versión nueva de «QA A»
sin la interfaz (alternativa), cambia la etiqueta y republica:

```bash
A=$(api "$BASE/autoria/subgrafos" | jq -r '.[]|select(.grafoId=="grafo_qa_a")|.id')
PUE=$(api "$BASE/autoria/subgrafos/$A" | jq -r .puenteId)
api -X PUT "$BASE/autoria/subgrafos/$A" -d "{\"nombre\":\"QA A\",\"puenteId\":\"$PUE\",\"etiquetaOpcion\":\"QA A v2\",\"autor\":\"QA\",\"institucion\":\"QA\"}" >/dev/null
publicar "$A" >/dev/null
psql -At -c "select id,origen from version_contenido order by id desc limit 2;"
```

La sesión que ya estaba en curso debe seguir viendo «QA A»; una nueva ve «QA A
v2».

## P-04 — Publicar una nueva versión de la base de reglas usada en una sesión activa

Pasos por interfaz: ver la hoja, P-04. Para cambiar R1 (de consulta a urgencia)
y publicar sin la interfaz (alternativa):

```bash
A=$(api "$BASE/autoria/subgrafos" | jq -r '.[]|select(.grafoId=="grafo_qa_a")|.id')
SUB=$(api "$BASE/autoria/subgrafos/$A")
P1=$(echo "$SUB"|jq -r '.nodos[]|select(.tipo=="PREGUNTA")|.id')
C_URG=$(echo "$SUB"|jq -r '.nodos[]|select(.texto=="Resultado urgente")|.id')
R=$(api "$BASE/autoria/bases" | jq -r '.[]|select(.grafoId=="grafo_qa_a")|.id')
RID=$(api "$BASE/autoria/bases/$R"|jq -r '.reglas[0].id')
api -X PUT "$BASE/autoria/bases/$R/reglas/$RID" -d "{\"nombre\":\"R1\",\"descripcion\":\"A2 ahora urgencia\",\"casos\":[[{\"idNodo\":\"$P1\",\"respuesta\":\"A2\"}]],\"nivel\":\"URGENCIA\",\"nodoDestino\":\"$C_URG\"}" >/dev/null
publicar "$A" >/dev/null   # las reglas salen con su subgrafo
psql -At -c "select id,origen from version_contenido order by id desc limit 2;"
```

La sesión en curso dispara la regla antigua (consulta); una nueva, la nueva
(urgencia).

## P-05 — Retirar un grafo/base de reglas mientras hay una sesión activa

Pasos por interfaz: ver la hoja, P-05. Antes de despublicar, crea una sesión y
déjala en la raíz (en la app o con):

```bash
REC=$(padre -X POST "$BASE/recorridos" -d '{}' | jq -r .recorridoId)   # a nombre del padre QA
echo "Sesión en curso: $REC"
```

Después de despublicar «QA A» (por interfaz), verifica que el subgrafo y su base
desaparecieron del contenido vigente y que se guardó una versión nueva:

```bash
psql -c "select count(*) as grafos_qa_a from grafo_clinico_entity where estructura->'graph'->>'id'='grafo_qa_a';"   # 0
psql -c "select count(*) as bases_qa_a  from base_reglas_entity  where estructura->>'grafoId'='grafo_qa_a';"        # 0
psql -c "select id, origen from version_contenido order by id desc limit 3;"                                         # última: "Despublicación de 'grafo_qa_a'"
```

La sesión en curso (`$REC`) debe seguir navegando con su versión (grafo y reglas
congelados): ingresa en `http://localhost:4200` como `qa.padre@signbb.local` (clave `clave-qa-2026`), abre `/pregunta/$REC` y termínala.

## P-06 — Despublicar borra la base de reglas y devuelve los borradores a BORRADOR

Pasos por interfaz: ver la hoja, P-06. Tras despublicar «QA A» por interfaz:

```bash
psql -c "select grafo_id, estado, (publicado_en is null) as sin_publicar, (copia_publicada is null) as sin_copia
         from borrador_base_regla_entity where grafo_id='grafo_qa_a';"   # estado = BORRADOR
```

Tras republicar «QA A» y publicar «QA R» por interfaz, vuelve a haber base
publicada y su regla:

```bash
psql -c "select count(*) as bases_qa_a from base_reglas_entity where estructura->>'grafoId'='grafo_qa_a';"  # 1
```

## P-07 — Despublicar un subgrafo sin base de reglas

Pasos por interfaz: ver la hoja, P-07. «QA B» nunca tiene base; antes y después
de despublicarlo:

```bash
psql -c "select count(*) as bases_qa_b from base_reglas_entity where estructura->>'grafoId'='grafo_qa_b';"  # 0
```

Y tras despublicar «QA B», «QA A» y su base no se tocan:

```bash
psql -At -c "select (select count(*) from grafo_clinico_entity where estructura->'graph'->>'id'='grafo_qa_a'),
                    (select count(*) from base_reglas_entity  where estructura->>'grafoId'='grafo_qa_a');"  # 1 1
```

## P-08 — Navegar con cero bases de reglas

Pasos por interfaz: ver la hoja, P-08. Requiere el contenido de ejemplo sembrado
(subgrafos respiración, gastrointestinal y actividad/color) más «QA A», «QA B» y
«QA L». Despublica por interfaz todos los subgrafos con base (los de ejemplo y
«QA A»), dejando publicados solo los que no tienen base («QA B» y «QA L»).
Comprueba que no queda ninguna base:

```bash
psql -c "select count(*) as bases_totales from base_reglas_entity;"   # 0
psql -c "select estructura->'graph'->>'id' as grafo_publicado
         from grafo_clinico_entity
         where estructura->'graph'->>'id' <> 'grafo_raiz';"            # incluye grafo_qa_b y grafo_qa_l
```

Luego inicia un recorrido nuevo por la categoría de «QA B» (o «QA L») y
termínalo (por conclusión, sin reglas, sin error 500).

### Restaurar el contenido de ejemplo (tras P-08)

P-08 deja el sistema sin bases y con varios subgrafos despublicados. Para volver
al estado inicial sembrado:

```bash
(cd signbb-backend/contenedores && docker compose down -v && docker compose up -d --wait)
# Rearranca el backend para que DatabaseInit vuelva a sembrar los grafos:
# (cd signbb-backend/backend && ./mvnw spring-boot:run)
```

> **Atención:** `docker compose down -v` borra **toda** la base de datos (incluido
> cualquier contenido de autoría que hayas creado aparte). Úsalo solo si quieres
> volver al contenido de ejemplo.

## Limpieza final (QA A/QA B/QA L)

Borra los subgrafos y bases de prueba (autocontenido, repetible):

```bash
BASE=http://localhost:8080/api
# Cuentas (rama seguridad): el contenido QA es del médico QA y lo publica el
# supervisor al aprobarlo. Las cuentas se crean la primera vez; si ya existen,
# la API responde 422 y se sigue.
SUP_CORREO=supervisor@signbb.local; SUP_CLAVE=Supervisor123; QA_CLAVE=clave-qa-2026
token(){ curl -s -H 'Content-Type: application/json' -X POST "$BASE/auth/login" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" | jq -r .token; }
TOK_SUP=$(token "$SUP_CORREO" "$SUP_CLAVE")
curl -s -o /dev/null -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_SUP" -X POST "$BASE/usuarios" -d "{\"nombre\":\"Médico QA\",\"correo\":\"qa.medico@signbb.local\",\"contrasena\":\"$QA_CLAVE\",\"rol\":\"MEDICO\",\"institucion\":\"QA\"}"
curl -s -o /dev/null -H 'Content-Type: application/json' -X POST "$BASE/auth/registro" -d "{\"nombre\":\"Padre QA\",\"correo\":\"qa.padre@signbb.local\",\"contrasena\":\"$QA_CLAVE\"}"
TOK_MED=$(token qa.medico@signbb.local "$QA_CLAVE"); TOK_PAD=$(token qa.padre@signbb.local "$QA_CLAVE")
api(){ curl -s -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_MED" "$@"; }    # médico QA
sup(){ curl -s -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_SUP" "$@"; }    # supervisor
padre(){ curl -s -H 'Content-Type: application/json' -H "Authorization: Bearer $TOK_PAD" "$@"; } # padre QA
for gid in grafo_qa_a grafo_qa_b grafo_qa_l; do
  id=$(api "$BASE/autoria/subgrafos" | jq -r ".[]|select(.grafoId==\"$gid\")|.id")
  [ -z "$id" ] && continue
  api -X POST "$BASE/autoria/subgrafos/$id/retirar-revision" >/dev/null
  estado=$(api "$BASE/autoria/subgrafos/$id" | jq -r .estado)
  [ "$estado" != BORRADOR ] && sup -X POST "$BASE/autoria/subgrafos/$id/despublicar" >/dev/null
  api -X DELETE "$BASE/autoria/subgrafos/$id" >/dev/null
done
for bid in $(api "$BASE/autoria/bases" | jq -r '.[]|select(.grafoId|startswith("grafo_qa"))|.id'); do
  api -X DELETE "$BASE/autoria/bases/$bid" >/dev/null
done
echo "QA A/QA B/QA L eliminados."
```
