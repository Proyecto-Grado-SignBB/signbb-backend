package org.signbb.backend.autoria.servicios;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.AccesoDenegadoException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica {@link PoliticaAccesoBorradores} antes de cada operación de la
 * autoría: los controladores la llaman con el id y el usuario y, si no
 * corresponde, responde 404 (no existe) o 403 ({@link
 * AccesoDenegadoException}). Así los servicios siguen recibiendo solo ids.
 * Un subgrafo en revisión, y su base de reglas, no se editan: 422 hasta que
 * el autor lo retire o un supervisor lo apruebe o rechace.
 */
@Component
@Transactional(readOnly = true)
public class GuardiaAutoria {

    /** Mensaje al intentar editar algo que está en revisión. */
    public static final String EN_REVISION = "Está en revisión; retíralo para editarlo.";

    private final RepositorioBorradorSubgrafo subgrafos;
    private final RepositorioBorradorBaseRegla bases;

    /** Inyecta los repositorios de borradores. */
    public GuardiaAutoria(RepositorioBorradorSubgrafo subgrafos, RepositorioBorradorBaseRegla bases) {
        this.subgrafos = subgrafos;
        this.bases = bases;
    }

    /** Exige que el usuario pueda ver el subgrafo. */
    public void leerSubgrafo(Long id, UsuarioActual usuario) {
        if (!PoliticaAccesoBorradores.puedeLeer(subgrafo(id).getAutorId(), usuario)) {
            throw new AccesoDenegadoException("Este subgrafo es de otro médico.");
        }
    }

    /** Exige que el usuario sea el autor del subgrafo y que no esté en revisión. */
    public void editarSubgrafo(Long id, UsuarioActual usuario) {
        BorradorSubgrafoEntity b = subgrafo(id);
        exigirAutor(b, usuario);
        exigirFueraDeRevision(b);
    }

    /**
     * Exige que el usuario sea un supervisor y el autor del subgrafo, y que no
     * esté en revisión. Si ya está publicado, solo lo deja republicar cuando su
     * base de reglas tiene cambios sin publicar.
     */
    public void publicarSubgrafo(Long id, UsuarioActual usuario) {
        BorradorSubgrafoEntity b = subgrafo(id);
        if (!usuario.esSupervisor()) {
            throw new AccesoDenegadoException("Solo un supervisor puede publicar directamente.");
        }
        if (!PoliticaAccesoBorradores.esAutor(b.getAutorId(), usuario)) {
            throw new AccesoDenegadoException("Solo puedes publicar directamente tus propios subgrafos.");
        }
        exigirFueraDeRevision(b);
        if (b.getEstado() == EstadoBorrador.PUBLICADO && !reglasConCambios(b)) {
            throw new EdicionInvalidaException("Este subgrafo ya está publicado y no tiene cambios.");
        }
    }

    /**
     * Exige que el usuario sea el autor del subgrafo, en cualquier estado: para
     * enviarlo a revisión o retirarlo.
     */
    public void autorSubgrafo(Long id, UsuarioActual usuario) {
        exigirAutor(subgrafo(id), usuario);
    }

    /** Exige que el usuario pueda ver la base de reglas. */
    public void leerBase(Long id, UsuarioActual usuario) {
        if (!PoliticaAccesoBorradores.puedeLeer(base(id).getAutorId(), usuario)) {
            throw new AccesoDenegadoException("Esta base de reglas es de otro médico.");
        }
    }

    /**
     * Exige que el usuario sea el autor de la base de reglas y que su subgrafo
     * no esté en revisión.
     */
    public void editarBase(Long id, UsuarioActual usuario) {
        BorradorBaseReglaEntity b = base(id);
        if (!PoliticaAccesoBorradores.puedeEditar(b.getAutorId(), usuario)) {
            throw new AccesoDenegadoException("Solo el autor puede editar esta base de reglas.");
        }
        if (b.getGrafoId() != null) {
            subgrafos.findByGrafoId(b.getGrafoId()).ifPresent(GuardiaAutoria::exigirFueraDeRevision);
        }
    }

    /**
     * Exige que el usuario pueda crear la base de reglas de ese grafo: el autor
     * de su subgrafo; para el grafo raíz o un grafo del sistema (sin borrador),
     * solo un supervisor. Si el grafo no existe lo dice después el servicio.
     */
    public void crearBase(String grafoId, UsuarioActual usuario) {
        var subgrafo = grafoId == null ? null : subgrafos.findByGrafoId(grafoId.strip()).orElse(null);
        if (subgrafo != null) {
            if (!PoliticaAccesoBorradores.puedeEditar(subgrafo.getAutorId(), usuario)) {
                throw new AccesoDenegadoException("Solo el autor del subgrafo puede crear sus reglas.");
            }
            exigirFueraDeRevision(subgrafo);
        } else if (grafoId != null && !grafoId.isBlank() && !usuario.esSupervisor()) {
            throw new AccesoDenegadoException("Las reglas del grafo raíz o de un grafo del sistema solo las "
                    + "crea un supervisor.");
        }
    }

    private void exigirAutor(BorradorSubgrafoEntity b, UsuarioActual usuario) {
        if (!PoliticaAccesoBorradores.puedeEditar(b.getAutorId(), usuario)) {
            throw new AccesoDenegadoException("Solo el autor puede editar este subgrafo.");
        }
    }

    private static void exigirFueraDeRevision(BorradorSubgrafoEntity b) {
        if (b.getEstado() == EstadoBorrador.EN_REVISION) {
            throw new EdicionInvalidaException(EN_REVISION);
        }
    }

    private boolean reglasConCambios(BorradorSubgrafoEntity b) {
        return bases.findByGrafoId(b.getGrafoId())
                .map(base -> base.getEstado() != EstadoBorrador.PUBLICADO)
                .orElse(false);
    }

    private BorradorSubgrafoEntity subgrafo(Long id) {
        return subgrafos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay un subgrafo con id " + id));
    }

    private BorradorBaseReglaEntity base(Long id) {
        return bases.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay una base de reglas con id " + id));
    }
}
