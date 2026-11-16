# Base de reglas: grafo_piel_color

| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |
| --- | --- | --- | --- | --- | --- |
| `R_PIEL_CON` | Consultar Piel y Color | `q_piel_color` = `Color amarillo en ojos, cara o hasta pecho` | `T_CONSULTA` | CONSULTAR_MEDICO |  |
| `R_PIEL_URG` | Urgencia Piel y Color | `q_piel_color` = `Amarillo por debajo del ombligo o piernas` ∨<br>`q_piel_color` = `Palmas de manos o plantas amarillas` ∨<br>`q_piel_color` = `Amarillo + muy dormido/no come` ∨<br>`q_piel_color` = `Amarillo en primeras 24 horas` | `T_URGENCIA` | URGENCIA |  |

## Resumen por nivel

| Nivel | Reglas |
| --- | --- |
| CONSULTAR_MEDICO | 1 |
| URGENCIA | 1 |
| **Total** | **2** |
