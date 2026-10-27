# Base de reglas: grafo_respiracion

| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |
| --- | --- | --- | --- | --- | --- |
| `R_RESP_CON` | Consultar Respiración | `q_resp_vista` = `Respira diferente sin esfuerzo` ∨<br>`q_resp_sonidos` = `Ruido al respirar que antes no hacía` ∨<br>`q_resp_sonidos` = `Presenta tos pero respira tranquilo` ∨<br>`q_resp_nariz` = `Congestión cuesta alimentar pero completa tomas` | `T_CONSULTA` | CONSULTAR_MEDICO |  |
| `R_RESP_URG` | Urgencia Respiración | `q_resp_vista` = `Respira persistentemente rápido (≥60/min)` ∨<br>`q_resp_vista` = `Aleteo nasal` ∨<br>`q_resp_vista` = `Se le hunde el pecho` ∨<br>`q_resp_vista` = `Labios o cara azules/grises` ∨<br>`q_resp_sonidos` = `Quejido al respirar` ∨<br>`q_resp_sonidos` = `Pausas o Apneas con cambio de color` ∨<br>`q_resp_nariz` = `Congestión no permite alimentarse` ∨<br>`q_resp_nariz` = `Más respiración rápida, quejido, etc.` | `T_URGENCIA` | URGENCIA |  |

## Resumen por nivel

| Nivel | Reglas |
| --- | --- |
| CONSULTAR_MEDICO | 1 |
| URGENCIA | 1 |
| **Total** | **2** |
