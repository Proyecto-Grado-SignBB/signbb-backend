# Base de reglas: grafo_actividad

| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |
| --- | --- | --- | --- | --- | --- |
| `R_ACT_CON` | Consultar Actividad y Comportamiento | `q_act_llanto` = `Más irritable/llora más, pero calma` | `T_CONSULTA` | CONSULTAR_MEDICO |  |
| `R_ACT_URG` | Urgencia Actividad y Comportamiento | `q_act_tono` = `Mucho más dormido / cuesta despertarlo` ∨<br>`q_act_tono` = `Más flojito de lo habitual` ∨<br>`q_act_tono` = `Más tieso de lo habitual / cuerpo rígido` ∨<br>`q_act_tono` = `Movimientos anormales o repetitivos` ∨<br>`q_act_llanto` = `Llanto inconsolable` | `T_URGENCIA` | URGENCIA |  |

## Resumen por nivel

| Nivel | Reglas |
| --- | --- |
| CONSULTAR_MEDICO | 1 |
| URGENCIA | 1 |
| **Total** | **2** |
