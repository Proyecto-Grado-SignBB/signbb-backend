package org.signbb.backend.autoria.servicios;

import java.time.Instant;
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
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.reglas.modelo.Caso;
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
    private final PublicadorRegla publicador;
    private final ResolutorCatalogoNodos catalogo;

    /** Inyecta los repositorios, el publicador y el catálogo de nodos. */
    public ServicioAutoriaReglasImpl(RepositorioBorradorBaseRegla borradores,
            RepositorioBorradorSubgrafo subgrafos, RepositorioGrafoClinico grafos,
            PublicadorRegla publicador, ResolutorCatalogoNodos catalogo) {
        this.borradores = borradores;
        this.subgrafos = subgrafos;
        this.grafos = grafos;
        this.publicador = publicador;
        this.catalogo = catalogo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumenBaseReglaDTO> listar() {
        return borradores.findAll(Sort.by(Sort.Direction.DESC, "actualizadoEn")).stream()
                .map(ResumenBaseReglaDTO::desde)
                .toList();
    }

    @Override
    @Transactional
    public BaseReglaDTO crear(BaseReglaRequest datos) {
        BorradorBaseReglaEntity b = new BorradorBaseReglaEntity();
        String grafoId = validarGrafo(datos);
        if (borradores.existsByGrafoId(grafoId)) {
            throw new EdicionInvalidaException("Ya hay una base de reglas para ese grafo.");
        }
        b.setGrafoId(grafoId);
        aplicarDatos(b, datos);
        b.setDocumento(DocumentoBaseRegla.vacio());
        b.setEstado(EstadoBorrador.BORRADOR);
        Instant ahora = Instant.now();
        b.setCreadoEn(ahora);
        b.setActualizadoEn(ahora);
        return BaseReglaDTO.desde(borradores.save(b));
    }

    @Override
    @Transactional(readOnly = true)
    public BaseReglaDTO obtener(Long id) {
        return BaseReglaDTO.desde(buscar(id));
    }

    @Override
    @Transactional
    public BaseReglaDTO actualizar(Long id, BaseReglaRequest datos) {
        BorradorBaseReglaEntity b = buscar(id);
        aplicarDatos(b, datos);
        marcarEditado(b);
        return BaseReglaDTO.desde(borradores.save(b));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        BorradorBaseReglaEntity b = buscar(id);
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
        return ValidadorReglas.validar(buscar(id).getDocumento());
    }

    @Override
    @Transactional
    public BaseReglaDTO publicar(Long id) {
        BorradorBaseReglaEntity b = buscar(id);
        exigirLienzoPublicado(b);
        Instant ahora = Instant.now();
        publicador.publicar(b, ahora);
        b.setEstado(EstadoBorrador.PUBLICADO);
        b.setPublicadoEn(ahora);
        return BaseReglaDTO.desde(borradores.save(b));
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoDTO catalogo(String grafoId) {
        return catalogo.catalogo(grafoId);
    }

    /** Aplica una operación del editor y guarda. */
    private BaseReglaDTO editar(Long id, UnaryOperator<DocumentoBaseRegla> operacion) {
        BorradorBaseReglaEntity b = buscar(id);
        b.setDocumento(operacion.apply(b.getDocumento()));
        marcarEditado(b);
        return BaseReglaDTO.desde(borradores.save(b));
    }

    private BorradorBaseReglaEntity buscar(Long id) {
        return borradores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay una base de reglas con id " + id));
    }

    private static void marcarEditado(BorradorBaseReglaEntity b) {
        b.setActualizadoEn(Instant.now());
        if (b.getEstado() == EstadoBorrador.PUBLICADO) {
            b.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        }
    }

    /** Valida y copia los datos del borrador; el grafo se fija al crear. */
    private void aplicarDatos(BorradorBaseReglaEntity b, BaseReglaRequest datos) {
        String nombre = datos == null ? null : limpio(datos.getNombre());
        if (nombre == null) {
            throw new EdicionInvalidaException("La base de reglas necesita un nombre.");
        }
        b.setNombre(nombre);
        b.setAutor(limpio(datos.getAutor()));
        b.setInstitucion(limpio(datos.getInstitucion()));
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
        if (CompositorGrafoClinico.ID_RAIZ.equals(grafoId) || subgrafos.existsByGrafoId(grafoId)
                || existeGrafoPublicado(grafoId)) {
            return grafoId;
        }
        throw new EdicionInvalidaException("Elige un grafo del sistema o un subgrafo que exista.");
    }

    private boolean existeGrafoPublicado(String grafoId) {
        return grafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .anyMatch(d -> d != null && d.graph() != null && grafoId.equals(d.graph().id()));
    }

    /**
     * Antes de publicar reglas de un subgrafo, el lienzo no puede tener cambios
     * sin publicar: las reglas deben apuntar a lo que la cuidadora ya navega.
     */
    private void exigirLienzoPublicado(BorradorBaseReglaEntity b) {
        if (CompositorGrafoClinico.ID_RAIZ.equals(b.getGrafoId())) {
            return;
        }
        subgrafos.findByGrafoId(b.getGrafoId())
                .filter(s -> s.getEstado() == EstadoBorrador.CAMBIOS_SIN_PUBLICAR)
                .ifPresent(s -> {
                    throw new EdicionInvalidaException(
                            "El lienzo del subgrafo cambió: republica el subgrafo antes que sus reglas.");
                });
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
