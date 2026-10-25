package org.signbb.backend.autoria.servicios;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.signbb.backend.autoria.controladores.BaseReglaDTO;
import org.signbb.backend.autoria.controladores.BaseReglaRequest;
import org.signbb.backend.autoria.controladores.CatalogoDTO;
import org.signbb.backend.autoria.controladores.ReglaRequest;
import org.signbb.backend.autoria.controladores.ResumenBaseReglaDTO;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.CopiaBaseReglaPublicada;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.reglas.modelo.Caso;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ServicioAutoriaReglas}: carga el borrador, aplica la
 * operación de {@link EditorReglas} y lo guarda. Editar un borrador ya
 * publicado lo deja en {@link EstadoBorrador#CAMBIOS_SIN_PUBLICAR}.
 */
@Service
public class ServicioAutoriaReglasImpl implements ServicioAutoriaReglas {

    private final RepositorioBorradorBaseRegla borradores;
    private final RepositorioBorradorSubgrafo subgrafos;
    private final RepositorioGrafoClinico grafos;
    private final ResolutorCatalogoNodos catalogo;

    /** Inyecta los repositorios y el catálogo de nodos. */
    public ServicioAutoriaReglasImpl(RepositorioBorradorBaseRegla borradores,
            RepositorioBorradorSubgrafo subgrafos, RepositorioGrafoClinico grafos,
            ResolutorCatalogoNodos catalogo) {
        this.borradores = borradores;
        this.subgrafos = subgrafos;
        this.grafos = grafos;
        this.catalogo = catalogo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumenBaseReglaDTO> listar(UsuarioActual usuario) {
        Sort orden = Sort.by(Sort.Direction.DESC, "actualizadoEn");
        List<BorradorBaseReglaEntity> lista = usuario.esSupervisor() ? borradores.findAll(orden)
                : borradores.findByAutorId(usuario.id(), orden);
        return lista.stream()
                .filter(b -> !PoliticaAccesoBorradores.esBaseRaiz(b.getGrafoId()))
                .map(ResumenBaseReglaDTO::desde)
                .toList();
    }

    @Override
    @Transactional
    public BaseReglaDTO crear(BaseReglaRequest datos, UsuarioActual usuario) {
        BorradorBaseReglaEntity b = new BorradorBaseReglaEntity();
        String grafoId = validarGrafo(datos);
        if (borradores.existsByGrafoId(grafoId)) {
            throw new EdicionInvalidaException("Ya hay una base de reglas para ese grafo.");
        }
        b.setGrafoId(grafoId);
        aplicarDatos(b, datos);
        b.setAutor(usuario.nombre());
        b.setInstitucion(usuario.institucion());
        b.setAutorId(usuario.id());
        b.setDocumento(DocumentoBaseRegla.vacio());
        b.setEstado(EstadoBorrador.BORRADOR);
        Instant ahora = Instant.now();
        b.setCreadoEn(ahora);
        b.setActualizadoEn(ahora);
        return dto(borradores.save(b));
    }

    @Override
    @Transactional(readOnly = true)
    public BaseReglaDTO obtener(Long id) {
        return dto(buscarVisible(id));
    }

    @Override
    @Transactional
    public BaseReglaDTO actualizar(Long id, BaseReglaRequest datos) {
        BorradorBaseReglaEntity b = buscarEditable(id);
        aplicarDatos(b, datos);
        marcarEditado(b);
        return dto(borradores.save(b));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        BorradorBaseReglaEntity b = buscarEditable(id);
        if (b.getPublicadoEn() != null) {
            throw new EdicionInvalidaException(
                    "Esta base de reglas ya está publicada y la cuidadora la usa: no se puede borrar.");
        }
        borradores.delete(b);
    }

    @Override
    @Transactional
    public BaseReglaDTO agregarRegla(Long id, ReglaRequest regla) {
        return editar(id, d -> EditorReglas.agregarRegla(d, datosDe(regla)));
    }

    @Override
    @Transactional
    public BaseReglaDTO editarRegla(Long id, String reglaId, ReglaRequest regla) {
        return editar(id, d -> EditorReglas.editarRegla(d, reglaId, datosDe(regla)));
    }

    @Override
    @Transactional
    public BaseReglaDTO eliminarRegla(Long id, String reglaId) {
        return editar(id, d -> EditorReglas.eliminarRegla(d, reglaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Aviso> avisos(Long id) {
        return avisosDe(buscarVisible(id));
    }

    /** Avisos de la base (estructura) más los de referencias al lienzo del subgrafo. */
    private List<Aviso> avisosDe(BorradorBaseReglaEntity b) {
        List<Aviso> avisos = new ArrayList<>(ValidadorReglas.validar(b.getDocumento()));
        if (!CompositorGrafoClinico.ID_RAIZ.equals(b.getGrafoId())) {
            subgrafos.findByGrafoId(b.getGrafoId()).ifPresent(s ->
                    avisos.addAll(ValidadorLienzoReglas.validar(s.getDocumento(), b.getDocumento())));
        }
        return avisos;
    }

    /** DTO del borrador con los avisos de estructura y de referencias al lienzo. */
    private BaseReglaDTO dto(BorradorBaseReglaEntity b) {
        return BaseReglaDTO.desde(b, avisosDe(b));
    }

    @Override
    @Transactional
    public BaseReglaDTO descartarCambios(Long id) {
        BorradorBaseReglaEntity b = buscarEditable(id);
        if (b.getPublicadoEn() == null) {
            throw new EdicionInvalidaException("Esta base de reglas todavía no se ha publicado, así que no hay "
                    + "una versión publicada a la que volver. Si ya no la quieres, puedes eliminarla.");
        }
        if (b.getEstado() == EstadoBorrador.PUBLICADO) {
            return dto(b);
        }
        CopiaBaseReglaPublicada copia = b.getCopiaPublicada();
        if (copia == null) {
            throw new EdicionInvalidaException("Esta base de reglas se publicó antes de que se guardara una "
                    + "copia de lo publicado. Podrás descartar cambios a partir de su próxima publicación.");
        }
        b.setDocumento(copia.documento());
        b.setNombre(copia.nombre());
        b.setDescripcion(copia.descripcion());
        b.setAutor(copia.autor());
        b.setInstitucion(copia.institucion());
        b.setEstado(EstadoBorrador.PUBLICADO);
        b.setActualizadoEn(Instant.now());
        return dto(borradores.save(b));
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoDTO catalogo(String grafoId) {
        if (PoliticaAccesoBorradores.esBaseRaiz(grafoId)) {
            throw new RecursoNoEncontradoException("no hay una base de reglas para el grafo raíz.");
        }
        return catalogo.catalogo(grafoId);
    }

    /** Aplica una operación del editor y guarda. */
    private BaseReglaDTO editar(Long id, UnaryOperator<DocumentoBaseRegla> operacion) {
        BorradorBaseReglaEntity b = buscarEditable(id);
        b.setDocumento(operacion.apply(b.getDocumento()));
        marcarEditado(b);
        return dto(borradores.save(b));
    }

    private BorradorBaseReglaEntity buscar(Long id) {
        return borradores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay una base de reglas con id " + id));
    }

    /** Una base visible: la raíz no forma parte del módulo de autoría. */
    private BorradorBaseReglaEntity buscarVisible(Long id) {
        BorradorBaseReglaEntity b = buscar(id);
        if (PoliticaAccesoBorradores.esBaseRaiz(b.getGrafoId())) {
            throw new RecursoNoEncontradoException("no hay una base de reglas para el grafo raíz.");
        }
        return b;
    }

    /** Una base editable: la raíz no se modifica desde autoría. */
    private BorradorBaseReglaEntity buscarEditable(Long id) {
        BorradorBaseReglaEntity b = buscar(id);
        if (PoliticaAccesoBorradores.esBaseRaiz(b.getGrafoId())) {
            throw new EdicionInvalidaException(PoliticaAccesoBorradores.REGLAS_RAIZ_SIN_BASE);
        }
        return b;
    }

    private static void marcarEditado(BorradorBaseReglaEntity b) {
        b.setActualizadoEn(Instant.now());
        if (b.getEstado() == EstadoBorrador.PUBLICADO) {
            b.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        }
    }

    /**
     * Valida y copia los datos del borrador; el grafo se fija al crear y el
     * autor sale de la cuenta al crear.
     */
    private void aplicarDatos(BorradorBaseReglaEntity b, BaseReglaRequest datos) {
        String nombre = datos == null ? null : limpio(datos.getNombre());
        if (nombre == null) {
            throw new EdicionInvalidaException("La base de reglas necesita un nombre.");
        }
        b.setNombre(nombre);
        b.setDescripcion(limpio(datos.getDescripcion()));
    }

    /**
     * Valida el grafo destino: {@code grafo_raiz}, un grafo publicado o un
     * borrador de subgrafo existente.
     */
    private String validarGrafo(BaseReglaRequest datos) {
        String grafoId = datos == null ? null : limpio(datos.getGrafoId());
        if (grafoId == null) {
            throw new EdicionInvalidaException("Elige el grafo al que pertenecen las reglas.");
        }
        if (PoliticaAccesoBorradores.esBaseRaiz(grafoId)) {
            throw new EdicionInvalidaException(PoliticaAccesoBorradores.REGLAS_RAIZ_SIN_BASE);
        }
        if (subgrafos.existsByGrafoId(grafoId) || existeGrafoPublicado(grafoId)) {
            return grafoId;
        }
        throw new EdicionInvalidaException("Elige un grafo del sistema o un subgrafo que exista.");
    }

    private boolean existeGrafoPublicado(String grafoId) {
        return grafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .anyMatch(d -> d != null && d.graph() != null && grafoId.equals(d.graph().id()));
    }

    /** Traduce la petición a los datos del editor; cada caso debe tener al menos una condición. */
    private static EditorReglas.DatosRegla datosDe(ReglaRequest regla) {
        if (regla == null) {
            return null;
        }
        List<Caso> casos = regla.getCasos() == null ? List.of()
                : regla.getCasos().stream().map(c -> new Caso(c == null ? List.of() : c)).toList();
        return new EditorReglas.DatosRegla(regla.getNombre(), regla.getDescripcion(), casos,
                regla.getNivel(), regla.getNodoDestino());
    }

    /** Texto sin espacios de los bordes, o {@code null} si queda vacío. */
    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
