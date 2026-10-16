package org.signbb.backend.observaciones.servicios;

import java.time.Instant;
import java.util.List;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.observaciones.controladores.ResumenRecorridoDTO;
import org.signbb.backend.observaciones.datos.entidades.RecorridoEntity;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioRecorridos;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementación de {@link ServicioRecorridos} sobre la tabla {@code recorrido}. */
@Service
public class ServicioRecorridosImpl implements ServicioRecorridos {

    /** Mensaje del 404 cuando el recorrido no existe o es de otro padre. */
    public static final String NO_DISPONIBLE = "Ese recorrido no está disponible.";

    /** Estado del recorrido terminado, como lo devuelve la navegación. */
    private static final String FINALIZADO = "FINALIZADO";

    private final ServicioNavegacion navegacion;
    private final RepositorioRecorridos recorridos;

    /** Inyecta la navegación y el repositorio de recorridos. */
    public ServicioRecorridosImpl(ServicioNavegacion navegacion, RepositorioRecorridos recorridos) {
        this.navegacion = navegacion;
        this.recorridos = recorridos;
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
    @Transactional(readOnly = true)
    public List<ResumenRecorridoDTO> deUsuario(UsuarioActual padre) {
        return recorridos.findByUsuarioIdOrderByIniciadoEnDesc(padre.id()).stream()
                .map(ResumenRecorridoDTO::desde)
                .toList();
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
