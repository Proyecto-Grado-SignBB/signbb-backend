package org.signbb.backend.observaciones.controladores;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cuerpo de la petición que inicia un recorrido. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IniciarRecorridoRequest {
    /** Id del grafo clínico a recorrer. */
    private long grafoClinicoId;
}
