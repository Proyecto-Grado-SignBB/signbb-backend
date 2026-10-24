package org.signbb.backend.observaciones.servicios;

import java.time.Instant;
import java.util.List;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.modelo.CandadoRecorrido;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.controladores.ResumenRecorridoDTO;
import org.signbb.backend.observaciones.datos.entidades.RecorridoEntity;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioRecorridos;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementación de {@link ServicioRecorridos} sobre la tabla {@code recorrido}.
 *
 * <p>
 * MODIFICADO (CU10/CU11): usa el {@link RepositorioSesion} para saber si la
 * sesión de un recorrido en curso venció ({@code VENCIDO}); "Mis recorridos"
 * se muestra igual si Redis no responde. Al reiniciar,
 * toma el {@link CandadoRecorrido} del recorrido, lo marca
 * {@code ABANDONADO} y empieza uno nuevo dentro de una transacción. La sesión
 * vieja se borra después de confirmar; si Redis falla en ese paso, la sesión
 * caduca sola y la fila ya no deja seguirla.
 */
@Service
public class ServicioRecorridosImpl implements ServicioRecorridos {

    /** Mensaje del 404 cuando el recorrido no existe o es de otro padre. */
    public static final String NO_DISPONIBLE = "Ese recorrido no está disponible.";

    /** Estado del recorrido que todavía tiene preguntas, como lo devuelve la navegación. */
    public static final String EN_CURSO = "EN_CURSO";

    /** Estado del recorrido terminado, como lo devuelve la navegación. */
    public static final String FINALIZADO = "FINALIZADO";

    /** Estado del recorrido en curso cuya sesión venció tras 8 h sin actividad. */
    public static final String VENCIDO = "VENCIDO";

    /** Estado del recorrido que el padre reinició; lo reemplaza uno nuevo. */
    public static final String ABANDONADO = "ABANDONADO";

    private static final Logger LOG = LoggerFactory.getLogger(ServicioRecorridosImpl.class);

    private final ServicioNavegacion navegacion;
    private final RepositorioRecorridos recorridos;
    private final RepositorioSesion sesiones;
    private final CandadoRecorrido candado;
    private final TransactionTemplate transaccion;

    /** Inyecta la navegación, los repositorios, el candado y el gestor de transacciones. */
    public ServicioRecorridosImpl(ServicioNavegacion navegacion, RepositorioRecorridos recorridos,
            RepositorioSesion sesiones, CandadoRecorrido candado, PlatformTransactionManager gestorTransacciones) {
        this.navegacion = navegacion;
        this.recorridos = recorridos;
        this.sesiones = sesiones;
        this.candado = candado;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    @Override
    @Transactional
    public RecorridoDTO iniciar(UsuarioActual padre) {
        RecorridoDTO dto = navegacion.iniciar();
        RecorridoEntity r = new RecorridoEntity();
        r.setId(dto.recorridoId());
        r.setUsuarioId(padre.id());
        r.setIniciadoEn(Instant.now());
        aplicar(r, dto);
        recorridos.save(r);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public void exigirDueno(String recorridoId, UsuarioActual padre) {
        boolean suyo = recorridoId != null && recorridos.findById(recorridoId)
                .map(r -> r.getUsuarioId().equals(padre.id()))
                .orElse(false);
        if (!suyo) {
            throw new RecursoNoEncontradoException(NO_DISPONIBLE);
        }
    }

    @Override
    @Transactional
    public RecorridoDTO actualizar(RecorridoDTO dto) {
        recorridos.findById(dto.recorridoId()).ifPresent(r -> {
            aplicar(r, dto);
            recorridos.save(r);
        });
        return dto;
    }

    @Override
    @Transactional
    public List<ResumenRecorridoDTO> deUsuario(UsuarioActual padre) {
        List<RecorridoEntity> filas = recorridos.findByUsuarioIdOrderByIniciadoEnDesc(padre.id());
        try {
            filas.forEach(this::vencerSiNoTieneSesion);
        } catch (RuntimeException e) {
            // sin Redis la lista se muestra igual, con el último estado guardado
            LOG.warn("No se pudo revisar qué recorridos vencieron: {}", e.toString());
        }
        return filas.stream().map(ResumenRecorridoDTO::desde).toList();
    }

    @Override
    @Transactional(noRollbackFor = RecorridoVencidoException.class)
    public void exigirVigente(String recorridoId) {
        RecorridoEntity r = recorridos.findById(recorridoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_DISPONIBLE));
        if (ABANDONADO.equals(r.getEstado())) {
            throw new RecorridoVencidoException(RecorridoVencidoException.MENSAJE_REINICIADO);
        }
        if (vencerSiNoTieneSesion(r) || VENCIDO.equals(r.getEstado())) {
            throw new RecorridoVencidoException();
        }
    }

    @Override
    public RecorridoDTO reiniciar(String recorridoId, UsuarioActual padre) {
        RecorridoDTO nuevo = candado.conCandado(recorridoId, () -> transaccion.execute(estado -> {
            RecorridoEntity r = recorridos.findById(recorridoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(NO_DISPONIBLE));
            if (FINALIZADO.equals(r.getEstado())) {
                throw new RecorridoFinalizadoException();
            }
            if (ABANDONADO.equals(r.getEstado())) {
                throw new RecorridoVencidoException(RecorridoVencidoException.MENSAJE_REINICIADO);
            }
            if (EN_CURSO.equals(r.getEstado())) {
                r.setEstado(ABANDONADO);
                recorridos.save(r);
            }
            return iniciar(padre);
        }));
        try {
            sesiones.eliminar(recorridoId);
        } catch (RuntimeException e) {
            LOG.warn("No se pudo borrar la sesión {} del recorrido reiniciado; caduca sola: {}",
                    recorridoId, e.toString());
        }
        return nuevo;
    }

    /**
     * Si el recorrido sigue en curso pero su sesión ya no existe, lo deja
     * {@code VENCIDO}.
     *
     * @return {@code true} si lo acaba de marcar vencido
     */
    private boolean vencerSiNoTieneSesion(RecorridoEntity r) {
        if (EN_CURSO.equals(r.getEstado()) && !sesiones.existe(r.getId())) {
            r.setEstado(VENCIDO);
            recorridos.save(r);
            return true;
        }
        return false;
    }

    /** Copia el estado y, si terminó, el resultado; al retroceder desde el final lo borra. */
    private static void aplicar(RecorridoEntity r, RecorridoDTO dto) {
        r.setEstado(dto.estado());
        if (FINALIZADO.equals(dto.estado()) && dto.resultado() != null) {
            r.setNivel(dto.resultado().nivel());
            r.setMensaje(dto.resultado().mensaje());
            if (r.getFinalizadoEn() == null) {
                r.setFinalizadoEn(Instant.now());
            }
        } else {
            r.setNivel(null);
            r.setMensaje(null);
            r.setFinalizadoEn(null);
        }
    }
}
