package org.signbb.backend.autoria.servicios;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
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
 */
@Component
@Transactional(readOnly = true)
public class GuardiaAutoria {

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

    /** Exige que el usuario sea el autor del subgrafo. */
    public void editarSubgrafo(Long id, UsuarioActual usuario) {
        exigirAutor(subgrafo(id), usuario);
    }

    /** Exige que el usuario pueda ver la base de reglas. */
    public void leerBase(Long id, UsuarioActual usuario) {
        if (!PoliticaAccesoBorradores.puedeLeer(base(id).getAutorId(), usuario)) {
            throw new AccesoDenegadoException("Esta base de reglas es de otro médico.");
        }
    }

    /** Exige que el usuario sea el autor de la base de reglas. */
    public void editarBase(Long id, UsuarioActual usuario) {
        BorradorBaseReglaEntity b = base(id);
        if (!PoliticaAccesoBorradores.puedeEditar(b.getAutorId(), usuario)) {
            throw new AccesoDenegadoException("Solo el autor puede editar esta base de reglas.");
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

    private BorradorSubgrafoEntity subgrafo(Long id) {
        return subgrafos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay un subgrafo con id " + id));
    }

    private BorradorBaseReglaEntity base(Long id) {
        return bases.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay una base de reglas con id " + id));
    }
}
