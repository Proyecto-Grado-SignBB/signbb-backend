package org.signbb.backend.observaciones.servicios;

import java.util.List;
import java.util.function.Supplier;

import org.signbb.backend.navegacion.modelo.CandadoRecorrido;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.datos.entidades.ObservacionEntity;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioObservaciones;
import org.signbb.backend.observaciones.modelo.SeleccionCuidador;
import org.signbb.backend.observaciones.modelo.TipoInteraccion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Captura de las respuestas del cuidador.
 *
 * <p>
 * Redis (sesión) y Postgres (observación y fila del recorrido) no comparten
 * transacción, así que cada cambio se hace así:
 * <ol>
 * <li>Se toma el candado del recorrido: una operación a la vez.</li>
 * <li>Se guarda una copia de la sesión tal como estaba.</li>
 * <li>Dentro de una transacción de Postgres se avanza la sesión en Redis y se
 * guardan la observación y el recorrido.</li>
 * <li>Si algo falla antes de terminar, Postgres revierte y la sesión vuelve a
 * la copia. Queda todo o nada.</li>
 * </ol>
 * Riesgo que se acepta: si el servidor se cae justo entre el avance en Redis y
 * el cierre de la transacción, la sesión queda avanzada sin su observación.
 */
@Service
public class ServicioCapturaImpl implements ServicioCaptura {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioCapturaImpl.class);

    private final ServicioNavegacion servicioNavegacion;
    private final RepositorioObservaciones repositorioObservaciones;
    private final ServicioRecorridos servicioRecorridos;
    private final RepositorioSesion repositorioSesion;
    private final CandadoRecorrido candado;
    private final TransactionTemplate transaccion;

    public ServicioCapturaImpl(ServicioNavegacion servicioNavegacion,
            RepositorioObservaciones repositorioObservaciones,
            ServicioRecorridos servicioRecorridos,
            RepositorioSesion repositorioSesion,
            CandadoRecorrido candado,
            PlatformTransactionManager gestorTransacciones) {
        this.servicioNavegacion = servicioNavegacion;
        this.repositorioObservaciones = repositorioObservaciones;
        this.servicioRecorridos = servicioRecorridos;
        this.repositorioSesion = repositorioSesion;
        this.candado = candado;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    @Override
    public RecorridoDTO registrarRespuesta(String recorridoId, TipoInteraccion tipo,
            List<String> opcionesSeleccionadas, Long usuarioId, String idPreguntaEsperada) {
        return candado.conCandado(recorridoId,
                () -> responderConCandado(recorridoId, tipo, opcionesSeleccionadas, usuarioId, idPreguntaEsperada));
    }

    @Override
    public RecorridoDTO retroceder(String recorridoId) {
        return candado.conCandado(recorridoId, () -> todoONada(recorridoId,
                () -> servicioRecorridos.actualizar(servicioNavegacion.retroceder(recorridoId))));
    }

    private RecorridoDTO responderConCandado(String recorridoId, TipoInteraccion tipo,
            List<String> opcionesSeleccionadas, Long usuarioId, String idPreguntaEsperada) {
        RecorridoDTO actual = servicioNavegacion.consultar(recorridoId);
        if (actual.pregunta() == null) {
            // el recorrido ya finalizó; no hay nada que registrar ni avanzar
            return actual;
        }
        String idNodo = actual.pregunta().id();
        if (idPreguntaEsperada != null && !idPreguntaEsperada.equals(idNodo)) {
            // la respuesta era para una pregunta que ya se contestó (clic repetido)
            throw new PreguntaDesactualizadaException();
        }

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
        return todoONada(recorridoId, () -> {
            RecorridoDTO resultado = servicioNavegacion.responder(recorridoId, opcionesSeleccionadas);
            repositorioObservaciones.save(ObservacionEntity.desde(seleccion, usuarioId));
            return servicioRecorridos.actualizar(resultado);
        });
    }

    /**
     * Hace el cambio dentro de una transacción de Postgres. Si falla (incluido
     * el cierre de la transacción), devuelve la sesión de Redis a como estaba
     * y deja salir el error original.
     */
    private RecorridoDTO todoONada(String recorridoId, Supplier<RecorridoDTO> cambio) {
        SesionTriaje antes = repositorioSesion.buscar(recorridoId);
        try {
            return transaccion.execute(estado -> cambio.get());
        } catch (RuntimeException e) {
            restaurar(antes, e);
            throw e;
        }
    }

    /** Devuelve la sesión a su copia; si tampoco se puede, queda en el registro y no tapa el error original. */
    private void restaurar(SesionTriaje antes, RuntimeException causa) {
        try {
            repositorioSesion.guardar(antes);
        } catch (RuntimeException e) {
            LOG.error("No se pudo devolver la sesión {} a su estado anterior", antes.getId(), e);
            causa.addSuppressed(e);
        }
    }
}
