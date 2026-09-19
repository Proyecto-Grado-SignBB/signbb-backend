package org.signbb.backend.observaciones.servicios;

import java.util.List;

import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.modelo.SeleccionCuidador;
import org.signbb.backend.observaciones.modelo.TipoInteraccion;
import org.signbb.backend.observaciones.repositorios.ObservacionEntity;
import org.signbb.backend.observaciones.repositorios.RepositorioObservaciones;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.springframework.stereotype.Service;

@Service
public class ServicioCapturaImpl implements ServicioCaptura {

    private final ServicioNavegacion servicioNavegacion;
    private final RepositorioObservaciones repositorioObservaciones;

    public ServicioCapturaImpl(ServicioNavegacion servicioNavegacion,
            RepositorioObservaciones repositorioObservaciones) {
        this.servicioNavegacion = servicioNavegacion;
        this.repositorioObservaciones = repositorioObservaciones;
    }

    @Override
    public RecorridoDTO registrarRespuesta(String recorridoId, TipoInteraccion tipo,
            List<String> opcionesSeleccionadas) {
        RecorridoDTO actual = servicioNavegacion.consultar(recorridoId);
        if (actual.pregunta() == null) {
            // el recorrido ya finalizó; no hay nada que registrar ni avanzar
            return actual;
        }
        String idNodo = actual.pregunta().id();

        // Construye y valida la selección (dispara IllegalArgumentException si
        // viene vacía, o si SI_NO/SELECCION_UNICA trae más de una opción).
        SeleccionCuidador seleccion = new SeleccionCuidador(recorridoId, idNodo, tipo, opcionesSeleccionadas);

        List<String> opcionesValidas = actual.pregunta().opciones();
        for (String op : opcionesSeleccionadas) {
            if (!opcionesValidas.contains(op)) {
                throw new IllegalArgumentException(
                        "'" + op + "' no es una opción válida para la pregunta '" + idNodo + "'");
            }
        }

        // Se entrega todo lo marcado, tal cual: la sobrecarga de
        // ServicioNavegacion ya sabe delegar al camino de un solo String
        // cuando la lista trae un único elemento.
        RecorridoDTO resultado = servicioNavegacion.responder(recorridoId, opcionesSeleccionadas);

        repositorioObservaciones.save(ObservacionEntity.desde(seleccion));
        return resultado;
    }
}