# Base de reglas: grafo_cabeza

| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |
| --- | --- | --- | --- | --- | --- |
| `R_CAB_CON` | Consultar Cabeza, Ojos y Oídos | `q_cab_ojos` = `Secreción repetida en ojo, sin hinchar` ∨<br>`q_cab_oidos` = `Líquido transparente del oído (no agua de baño)` | `T_CONSULTA` | CONSULTAR_MEDICO |  |
| `R_CAB_URG` | Urgencia Cabeza, Ojos y Oídos | `q_cab_gate_fontanela` = `Sí` ∨<br>`q_cab_ojos` = `Pus en ojo e hinchado/rojo` ∨<br>`q_cab_oidos` = `Pus o secreción amarilla/verdosa de oído` ∨<br>`q_cab_oidos` = `Sangre de oído` | `T_URGENCIA` | URGENCIA |  |

## Resumen por nivel

| Nivel | Reglas |
| --- | --- |
| CONSULTAR_MEDICO | 1 |
| URGENCIA | 1 |
| **Total** | **2** |
