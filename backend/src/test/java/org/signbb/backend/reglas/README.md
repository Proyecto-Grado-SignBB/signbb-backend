# Pruebas del módulo de reglas {#pruebas_reglas}

> Índice general de la suite: \ref pruebas.

Pruebas del paquete `org.signbb.backend.reglas`: los **TAD** de reglas
(`reglas.modelo`), el **servicio de inferencia** (`reglas.servicios`) y el
**mapeo JSON** de la base de reglas (`reglas.datos.serializacion`). Son
**JUnit 5 puro** y, en la suite abstracta, **Mockito** para simular los TADs
`EstadoPaciente` y `Nodo`.

## Mapa de la suite

| Paquete | Clase | Tests | Qué cubre |
|---|---|---:|---|
| `modelo` | `ReglaClinicaTest` | 7 | creación y validación de `ReglaClinica`; condiciones AND cumplidas/incumplidas; igualdad por `idRegla`; copia defensiva |
| `modelo` | `BaseReglasTest` | 7 | alta/baja de reglas, duplicados, `obtenerRegla` y `reglasActivadas` |
| `modelo` | `EvaluacionReglasAbstractaTest` | 2 | FND con varios casos (OR) y varias opciones de la misma pregunta (AND) |
| `servicios` | `ServicioReglasImplTest` | 4 | resolución por severidad: nivel máximo, razones combinadas y desempate por peso del destino |
| `datos.serializacion` | `BaseReglasMapperTest` | 3 | ida y vuelta TAD ↔ documento JSON y rechazo de un `nodoDestino` colgante |

En total son **23 pruebas**.

## Modelo de reglas (TAD) {#pruebas_model}

### TAD `ReglaClinica` (`ReglaClinicaTest`)
La premisa es una fórmula en **forma normal disyuntiva** (FND): un caso es una
conjunción (AND) de condiciones y la regla se satisface si **al menos un** caso
se cumple (OR). El constructor plano (lista de `Condicion`) equivale a un único
caso (AND). Se verifica que:

- una regla sin condiciones o con componentes nulos se rechaza;
- `condicionesCumplidas` exige **todas** las condiciones del caso;
- el destino, el nivel y las condiciones hoja se exponen como copias;
- la identidad de la regla está dada por `idRegla`.

### TAD `BaseReglas` (`BaseReglasTest`)
Colección indexada por `idRegla`. Se verifica el alta y la baja, el rechazo de
duplicados y que `reglasActivadas` devuelve solo las reglas cuya premisa se
satisface en el estado.

### Evaluación abstracta (`EvaluacionReglasAbstractaTest`)
Conserva los dos casos que no cubren las otras suites, con datos
**abstractos** (`resp1/op1/op2/op3`) y los TADs `EstadoPaciente` y `Nodo`
**simulados con Mockito**:

- varios casos (OR) armados con `ReglaClinica.conCasos`;
- varias opciones de la **misma pregunta** combinadas con AND (selección
  múltiple).

## Servicio de inferencia y mapeo JSON

### Servicio de inferencia (`ServicioReglasImplTest`)
Implementa la fase de **resolución de conflicto** del motor de reglas
(Algoritmo 3 del diseño): cuando varias reglas se activan a la vez, gana el
**nivel más severo** (`NORMAL < CONSULTAR_MEDICO < URGENCIA`); entre las del
nivel máximo se **combinan todas como razones** y el destino es el de **mayor
peso** (el signo más crítico). Se cubre:

- dos reglas del mismo nivel que combinan sus razones;
- ninguna regla activa → resultado `NORMAL` sin destino;
- niveles mixtos → gana el más severo;
- empate de nivel con destinos de distinto peso → gana el de mayor peso.

### Mapeo JSON (`BaseReglasMapperTest`)
Verifica la ida y vuelta entre el TAD `BaseReglas` y el documento JSON de
intercambio, que conserva la **forma normal disyuntiva** en `condiciones`:

- reconstrucción fiel desde el contenido compuesto del sistema (tamaño, nombre,
  nivel, destino y condiciones de cada regla);
- el `nodoDestino` se serializa **por id** (nunca se embebe el nodo);
- un `nodoDestino` que no existe en el grafo se **rechaza** al reconstruir.

## Cómo ejecutarlas

    ./mvnw test -Dtest='org.signbb.backend.reglas.**'

No requieren PostgreSQL ni Redis.

> La autoría de reglas (CRUD del borrador y publicación) está en
> \ref pruebas_autoria.
