# Base de reglas: grafo_gastro

| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |
| --- | --- | --- | --- | --- | --- |
| `R_GASTRO_CON` | Consultar Gastrointestinal | `q_gastro_vomito` = `Ha vomitado varias veces` ∨<br>`q_gastro_alimentacion` = `Come mucho menos o succiona débil` ∨<br>`q_gastro_alimentacion` = `Se queda dormido repetidamente al comer` ∨<br>`q_gastro_alimentacion` = `Tarda mucho más de lo habitual` ∨<br>`q_gastro_abdomen` = `Barriga más hinchada, pero blanda y come bien` ∨<br>`q_gastro_popo` = `Cambió frecuencia o consistencia` ∨<br>`q_gastro_popo` = `Mucho moco repetido` ∨<br>`q_gastro_popo` = `Muchas más o menos deposiciones` ∨<br>`q_gastro_popo` = `Deposiciones más líquidas de lo habitual` ∨<br>`q_gastro_orina` = `Mojando menos pañales` ∨<br>`q_gastro_orina` = `Olor diferente o fuerte persistente` ∨<br>`q_gastro_orina` = `Llora/molesta al orinar` | `T_CONSULTA` | CONSULTAR_MEDICO |  |
| `R_GASTRO_URG` | Urgencia Gastrointestinal | `q_gastro_vomito` = `Vomita todo lo que ingiere` ∨<br>`q_gastro_vomito` = `Vomita con fuerza/sale disparado` ∨<br>`q_gastro_alimentacion` = `No logra alimentarse o rechaza pecho` ∨<br>`q_gastro_alimentacion` = `Tose, atraganta, deja de respirar o cambia color al comer` ∨<br>`q_gastro_abdomen` = `Barriga muy hinchada y dura/tensa` ∨<br>`q_gastro_abdomen` = `Barriga hinchada + vómito o sangre en popó` ∨<br>`q_gastro_abdomen` = `Barriga hinchada + no quiere comer o está decaído` ∨<br>`q_gastro_popo` = `Popó blanco o gris claro` ∨<br>`q_gastro_popo` = `Sangre roja en popó` ∨<br>`q_gastro_popo` = `No hace popó + barriga hinchada o vómito` ∨<br>`q_gastro_orina` = `Sangre roja en orina/pañal` ∨<br>`q_gastro_orina` = `Muchas horas sin mojar ningún pañal` | `T_URGENCIA` | URGENCIA |  |

## Resumen por nivel

| Nivel | Reglas |
| --- | --- |
| CONSULTAR_MEDICO | 1 |
| URGENCIA | 1 |
| **Total** | **2** |
