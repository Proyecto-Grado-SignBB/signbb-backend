| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |
| --- | --- | --- | --- | --- | --- |
| `R_GASTRO_CON` | Regla Gastro Consulta | `q_vomito_frecuencia` = `SI` ∨<br>`q_abdomen_blando` = `SI` ∨<br>`q_popo_cambio` = `SI` | `consulta_1` | CONSULTAR_MEDICO |  |
| `R_GASTRO_NOR` | Regla Gastro Normal | `q_vomito_frecuencia` = `NO` ∨<br>`q_abdomen_blando` = `NO` ∨<br>`q_popo_cambio` = `NO` | `normal_1` | NORMAL |  |
| `R_GASTRO_URG` | Regla Gastro Urgencia | `q_intermedia_vomito` = `SI` ∨<br>`q_intermedia_abdomen` = `SI` ∨<br>`q_intermedia_deposicion` = `SI` | `urgencia_1` | URGENCIA |  |
| `R_NEURO_CON` | Regla Neurológico y Color Consulta | `q_tono_extremo` = `NO` ∨<br>`q_tono_estimulo` = `NO` ∨<br>`q_color_ictericia_cara` = `NO` ∨<br>`q_llanto_calma` = `SI` ∨<br>`q_llanto_comun` = `SI` | `consulta_1` | CONSULTAR_MEDICO |  |
| `R_NEURO_NOR` | Regla Neurológico y Color Normal | `q_tono_estimulo` = `SI` ∨<br>`q_color_cianosis` = `NO` ∨<br>`q_llanto_comun` = `NO` | `normal_1` | NORMAL |  |
| `R_NEURO_URG` | Regla Neurológico y Color Urgencia | `q_tono_extremo` = `SI` ∨<br>`q_color_ictericia_cara` = `SI` ∨<br>`q_color_cianosis` = `SI` ∨<br>`q_llanto_calma` = `NO` | `urgencia_1` | URGENCIA |  |
| `R_RESP_CON` | Regla Respiración Consulta | `q_resp_consulta` = `Congestión que dificulta alimentarse` ∨<br>`q_resp_consulta` = `Ruido al respirar que antes no hacía` ∨<br>`q_resp_consulta` = `Respira diferente sin esfuerzo` ∨<br>`q_resp_consulta` = `Presenta tos pero respira tranquilo` | `consulta_1` | CONSULTAR_MEDICO |  |
| `R_RESP_NOR` | Regla Respiración Normal | `q_resp_normal` = `Pausas cortas sin cambiar de color` ∨<br>`q_resp_normal` = `Ruiditos pero respira tranquilo` ∨<br>`q_resp_normal` = `Estornudos o hipo` ∨<br>`q_resp_normal` = `Congestión nasal leve` ∨<br>`q_resp_normal` = `No, no presenta anomalías` | `normal_1` | NORMAL |  |
| `R_RESP_URG` | Regla Respiración Urgencia | `q_resp_urgencia` = `Respira persistentemente rápido (≥60/min)` ∨<br>`q_resp_urgencia` = `Quejido al respirar` ∨<br>`q_resp_urgencia` = `Aleteo nasal` ∨<br>`q_resp_urgencia` = `Se le hunde el pecho` ∨<br>`q_resp_urgencia` = `Pausas o Apneas con cambio de color` ∨<br>`q_resp_urgencia` = `Labios o cara azules/grises` | `urgencia_1` | URGENCIA |  |

## Resumen por nivel

| Nivel | Reglas |
| --- | --- |
| CONSULTAR_MEDICO | 3 |
| NORMAL | 3 |
| URGENCIA | 3 |
| **Total** | **9** |
