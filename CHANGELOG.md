# Changelog — SignBB backend

## [Sin publicar]

### Añadido
- **Ejemplo de normalización de base de datos (1NF, 2NF, 3NF)** como segundo
  grafo de demostración, no clínico, con pesos en el rango 1–3:
  - `src/main/resources/static/ejemplo_grafo_norm.json`
  - `src/main/resources/static/ejemplo_reglas_norm.json`
- **Pruebas de navegación** de los tres comportamientos pedidos (rojo de mayor
  peso, selección múltiple con regla AND, selección única):
  `MotorNavegacionNormalizacionTest`.
- **Ejemplo de triaje de ciberseguridad** (correo, contraseñas y archivos
  sospechosos), un grafo más complejo con tres categorías y terminales
  rojo/amarillo/verde:
  - `src/main/resources/static/ejemplo_grafo_sec.json`
  - `src/main/resources/static/ejemplo_reglas_sec.json` (R5 usa AND con OR
    anidados)
- **Pruebas de navegación** del ejemplo de seguridad (desempate por prioridad
  con varias opciones, regla que acumula respuestas de distintas preguntas y
  regla con AND/OR anidados): `MotorNavegacionSeguridadTest`.

### Cambiado
- **Redirección por regla:** si el `nodoDestino` de la regla ganadora es una
  pregunta, el motor continúa el recorrido desde ese nodo en vez de terminar
  (regla "gate"). El nivel de la regla gate no se reporta como alerta; el
  desenlace final toma el nivel del terminal alcanzado. (`MotorNavegacion`)
- **Multi-respuesta por nodo:** `EstadoPaciente` guarda todas las opciones
  marcadas de una pregunta de selección múltiple y las expone con
  `getRespuestas`; `Condicion` se satisface si la respuesta esperada está entre
  las registradas, lo que habilita reglas AND sobre una misma pregunta. El
  `paso` de selección múltiple registra todas las opciones y usa una cola de
  prioridad por peso del destino para interrumpir en el terminal más grave.
- **Reglas consumidas:** las reglas que ya se dispararon no se reevalúan en los
  pasos siguientes (`BaseReglas.reglasActivadas` + `EstadoPaciente`), evitando
  que una regla gate se reactive en bucle.
- **Sesiones en Redis:** `SesionJson` persiste varias respuestas por nodo y las
  reglas disparadas; `RepositorioSesionRedis` las reconstruye.
- **Desempate estable en selección múltiple:** `opcionMasGrave` usa una cola de
  prioridad con desempate por índice de la opción, de modo que a igual peso
  gana siempre la primera marcada (la `PriorityQueue` sin desempate no
  garantizaba el orden de inserción).

## Pendiente
1. Cuando recibe múltiples opciones ordena en una cola de prioridad iniciando
   por las de mayor prioridad; cuando termina de explorar la rama hace
   backtracking.
   - Interrumpe el recorrido al llegar a un estado urgente.
