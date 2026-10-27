# Base de reglas: grafo_activo_color

| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |
| --- | --- | --- | --- | --- | --- |
| `R_NEURO_CON` | Consultar Actividad y Color | `q_neuro_llanto` = `Más irritable/llora más, pero calma` ∨<br>`q_neuro_color` = `Color amarillo en ojos, cara o hasta pecho` | `T_CONSULTA` | CONSULTAR_MEDICO |  |
| `R_NEURO_URG` | Urgencia Actividad y Color | `q_neuro_tono` = `Mucho más dormido / cuesta despertarlo` ∨<br>`q_neuro_tono` = `Más flojito de lo habitual` ∨<br>`q_neuro_tono` = `Más tieso de lo habitual / cuerpo rígido` ∨<br>`q_neuro_tono` = `Movimientos anormales o repetitivos` ∨<br>`q_neuro_llanto` = `Llanto inconsolable` ∨<br>`q_neuro_color` = `Amarillo por debajo del ombligo o piernas` ∨<br>`q_neuro_color` = `Palmas de manos o plantas amarillas` ∨<br>`q_neuro_color` = `Amarillo + muy dormido/no come` ∨<br>`q_neuro_color` = `Amarillo en primeras 24 horas` | `T_URGENCIA` | URGENCIA |  |

## Resumen por nivel

| Nivel | Reglas |
| --- | --- |
| CONSULTAR_MEDICO | 1 |
| URGENCIA | 1 |
| **Total** | **2** |
