package org.signbb.backend.autoria.servicios;

import java.time.Instant;
import java.util.List;

import org.signbb.backend.autoria.controladores.ResumenSubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.AccesoDenegadoException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ServicioRevision}. Aprobar reutiliza {@link
 * ServicioAutoria#publicar}, así que publica el subgrafo y su base de reglas
 * en una sola acción, con las mismas validaciones. Por eso un subgrafo
 * publicado también puede enviarse cuando lo único que cambió son sus reglas.
 */
@Service
public class ServicioRevisionImpl implements ServicioRevision {

    private final RepositorioBorradorSubgrafo borradores;
    private final RepositorioBorradorBaseRegla bases;
    private final ServicioAutoria autoria;

    /** Inyecta los repositorios de borradores y bases de reglas, y el servicio de autoría. */
    public ServicioRevisionImpl(RepositorioBorradorSubgrafo borradores, RepositorioBorradorBaseRegla bases,
            ServicioAutoria autoria) {
        this.borradores = borradores;
        this.bases = bases;
        this.autoria = autoria;
    }

    @Override
    @Transactional
    public SubgrafoDTO enviar(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        if (b.getEstado() == EstadoBorrador.EN_REVISION) {
            throw new EdicionInvalidaException("Este subgrafo ya está en revisión.");
        }
        if (b.getEstado() == EstadoBorrador.PUBLICADO && !reglasSinPublicar(b)) {
            throw new EdicionInvalidaException("Este subgrafo está publicado y no tiene cambios que revisar.");
        }
        List<Aviso> bloqueantes = autoria.avisos(id).stream().filter(Aviso::bloqueante).toList();
        if (!bloqueantes.isEmpty()) {
            throw new ContenidoInvalidoException(
                    "No se pudo enviar a revisión: revisa los avisos del lienzo y de las reglas.", bloqueantes);
        }
        b.setEstado(EstadoBorrador.EN_REVISION);
        b.setEnviadoRevisionEn(Instant.now());
        b.setComentarioRevision(null);
        borradores.save(b);
        return autoria.obtener(id);
    }

    @Override
    @Transactional
    public SubgrafoDTO retirar(Long id) {
        BorradorSubgrafoEntity b = enRevision(id);
        b.setEstado(estadoFueraDeRevision(b));
        borradores.save(b);
        return autoria.obtener(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumenSubgrafoDTO> pendientes(UsuarioActual supervisor) {
        return borradores.findByEstado(EstadoBorrador.EN_REVISION, Sort.by("enviadoRevisionEn")).stream()
                .filter(b -> !PoliticaAccesoBorradores.esAutor(b.getAutorId(), supervisor))
                .map(ResumenSubgrafoDTO::desde)
                .toList();
    }

    @Override
    @Transactional
    public SubgrafoDTO aprobar(Long id, UsuarioActual supervisor) {
        BorradorSubgrafoEntity b = enRevision(id);
        exigirOtroRevisor(b, supervisor);
        autoria.publicar(id);
        b.setRevisorId(supervisor.id());
        b.setRevisadoEn(Instant.now());
        b.setComentarioRevision(null);
        borradores.save(b);
        return autoria.obtener(id);
    }

    @Override
    @Transactional
    public SubgrafoDTO rechazar(Long id, String comentario, UsuarioActual supervisor) {
        String texto = comentario == null || comentario.isBlank() ? null : comentario.strip();
        if (texto == null) {
            throw new EdicionInvalidaException("Escribe un comentario para que el autor sepa qué corregir.");
        }
        BorradorSubgrafoEntity b = enRevision(id);
        exigirOtroRevisor(b, supervisor);
        b.setEstado(estadoFueraDeRevision(b));
        b.setRevisorId(supervisor.id());
        b.setRevisadoEn(Instant.now());
        b.setComentarioRevision(texto);
        borradores.save(b);
        return autoria.obtener(id);
    }

    /** Estado al salir de revisión sin publicar. */
    private static EstadoBorrador estadoFueraDeRevision(BorradorSubgrafoEntity b) {
        return b.getPublicadoEn() == null ? EstadoBorrador.BORRADOR : EstadoBorrador.CAMBIOS_SIN_PUBLICAR;
    }

    private static void exigirOtroRevisor(BorradorSubgrafoEntity b, UsuarioActual supervisor) {
        if (PoliticaAccesoBorradores.esAutor(b.getAutorId(), supervisor)) {
            throw new AccesoDenegadoException("No puedes revisar un subgrafo tuyo: lo revisa otro supervisor.");
        }
    }

    private BorradorSubgrafoEntity enRevision(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        if (b.getEstado() != EstadoBorrador.EN_REVISION) {
            throw new EdicionInvalidaException("Este subgrafo no está en revisión.");
        }
        return b;
    }

    private BorradorSubgrafoEntity buscar(Long id) {
        return borradores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay un subgrafo con id " + id));
    }

    /** True si la base de reglas del subgrafo tiene cambios que aún no salieron con él. */
    private boolean reglasSinPublicar(BorradorSubgrafoEntity b) {
        return bases.findByGrafoId(b.getGrafoId())
                .map(base -> base.getEstado() != EstadoBorrador.PUBLICADO)
                .orElse(false);
    }
}
