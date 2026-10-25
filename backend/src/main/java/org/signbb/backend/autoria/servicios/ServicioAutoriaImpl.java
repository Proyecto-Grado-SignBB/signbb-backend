package org.signbb.backend.autoria.servicios;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.signbb.backend.autoria.controladores.CategoriaDTO;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.OpcionRequest;
import org.signbb.backend.autoria.controladores.ResumenSubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.CopiaBaseReglaPublicada;
import org.signbb.backend.autoria.modelo.CopiaPublicada;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.media.ServicioImagenes;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Implementación de {@link ServicioAutoria}: carga el borrador, aplica la
 * operación de {@link EditorLienzo} y lo guarda. Editar un borrador ya
 * publicado lo deja en {@link EstadoBorrador#CAMBIOS_SIN_PUBLICAR}.
 */
@Service
public class ServicioAutoriaImpl implements ServicioAutoria {

    private static final Logger log = LoggerFactory.getLogger(ServicioAutoriaImpl.class);

    /** Largo máximo del slug del {@code grafoId}. */
    private static final int LARGO_SLUG = 40;

    private final RepositorioBorradorSubgrafo borradores;
    private final RepositorioBorradorBaseRegla basesReglas;
    private final RepositorioBaseReglas basesPublicadas;
    private final RepositorioGrafoClinico grafos;
    private final PublicadorSubgrafo publicador;
    private final PublicadorRegla publicadorRegla;
    private final ResolvedorVersiones versiones;
    private final ServicioImagenes imagenes;

    /** Inyecta los repositorios, los publicadores, el resolvedor de versiones y las imágenes. */
    public ServicioAutoriaImpl(RepositorioBorradorSubgrafo borradores,
            RepositorioBorradorBaseRegla basesReglas, RepositorioBaseReglas basesPublicadas,
            RepositorioGrafoClinico grafos, PublicadorSubgrafo publicador, PublicadorRegla publicadorRegla,
            ResolvedorVersiones versiones, ServicioImagenes imagenes) {
        this.borradores = borradores;
        this.basesReglas = basesReglas;
        this.basesPublicadas = basesPublicadas;
        this.grafos = grafos;
        this.publicador = publicador;
        this.publicadorRegla = publicadorRegla;
        this.versiones = versiones;
        this.imagenes = imagenes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaDTO> categorias() {
        GrafoClinicoJson.Grafo raiz = raiz();
        List<CategoriaDTO> categorias = new ArrayList<>();
        for (GrafoClinicoJson.Arista a : raiz.edges() == null ? List.<GrafoClinicoJson.Arista>of() : raiz.edges()) {
            GrafoClinicoJson.NodoJson puente = raiz.nodes().get(a.target());
            if (a.source().equals(raiz.preguntaInicial()) && puente != null && puente.metadata() != null
                    && "pregunta".equals(puente.metadata().tipo())) {
                categorias.add(new CategoriaDTO(a.target(), a.relation(), puente.label()));
            }
        }
        return categorias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumenSubgrafoDTO> listar(UsuarioActual usuario) {
        Sort orden = Sort.by(Sort.Direction.DESC, "actualizadoEn");
        List<BorradorSubgrafoEntity> lista = usuario.esSupervisor() ? borradores.findAll(orden)
                : borradores.findByAutorId(usuario.id(), orden);
        return lista.stream()
                .map(ResumenSubgrafoDTO::desde)
                .toList();
    }

    @Override
    @Transactional
    public SubgrafoDTO crear(SubgrafoRequest datos, UsuarioActual usuario) {
        BorradorSubgrafoEntity b = new BorradorSubgrafoEntity();
        aplicarDatos(b, datos);
        b.setAutor(usuario.nombre());
        b.setInstitucion(usuario.institucion());
        b.setAutorId(usuario.id());
        b.setGrafoId(grafoIdLibre(b.getNombre()));
        b.setDocumento(DocumentoLienzo.vacio());
        b.setEstado(EstadoBorrador.BORRADOR);
        Instant ahora = Instant.now();
        b.setCreadoEn(ahora);
        b.setActualizadoEn(ahora);
        return dto(borradores.save(b));
    }

    @Override
    @Transactional(readOnly = true)
    public SubgrafoDTO obtener(Long id) {
        return dto(buscar(id));
    }

    @Override
    @Transactional
    public SubgrafoDTO actualizar(Long id, SubgrafoRequest datos) {
        BorradorSubgrafoEntity b = buscar(id);
        aplicarDatos(b, datos);
        marcarEditado(b);
        return dto(borradores.save(b));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        if (b.getPublicadoEn() != null) {
            throw new EdicionInvalidaException(
                    "Este subgrafo ya está publicado y las cuidadoras lo usan: no se puede borrar.");
        }
        Set<String> antes = imagenesDe(b.getDocumento());
        basesReglas.findByGrafoId(b.getGrafoId()).ifPresent(basesReglas::delete);
        borradores.delete(b);
        programarBorrado(antes, Set.of());
    }

    @Override
    @Transactional
    public SubgrafoDTO agregarNodo(Long id, NodoRequest nodo) {
        String despuesDeNodo = nodo == null || nodo.getDespuesDe() == null ? null : nodo.getDespuesDe().getNodoId();
        String respuestaId = nodo == null || nodo.getDespuesDe() == null ? null
                : nodo.getDespuesDe().getRespuestaId();
        return editar(id, d -> EditorLienzo.agregarNodo(d, despuesDeNodo, respuestaId, datosDe(nodo)));
    }

    @Override
    @Transactional
    public SubgrafoDTO enlazar(Long id, String respuestaId, String nodoId) {
        return editar(id, d -> EditorLienzo.enlazar(d, respuestaId, nodoId));
    }

    @Override
    @Transactional
    public SubgrafoDTO editarNodo(Long id, String nodoId, NodoRequest nodo) {
        BorradorSubgrafoEntity b = buscar(id);
        Set<String> antes = imagenesDe(b.getDocumento());
        b.setDocumento(EditorLienzo.editarNodo(b.getDocumento(), nodoId, datosDe(nodo)));
        Set<String> despues = imagenesDe(b.getDocumento());
        marcarEditado(b);
        SubgrafoDTO dto = dto(borradores.save(b));
        programarBorrado(antes, despues);
        return dto;
    }

    @Override
    @Transactional
    public SubgrafoDTO eliminarNodo(Long id, String nodoId) {
        BorradorSubgrafoEntity b = buscar(id);
        Set<String> antes = imagenesDe(b.getDocumento());
        b.setDocumento(EditorLienzo.eliminarNodo(b.getDocumento(), nodoId));
        marcarEditado(b);
        SubgrafoDTO dto = dto(borradores.save(b));
        programarBorrado(antes, Set.of());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Aviso> avisos(Long id) {
        return avisosDe(buscar(id));
    }

    @Override
    @Transactional
    public SubgrafoDTO publicar(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        List<Aviso> bloqueantes = avisosDe(b).stream().filter(Aviso::bloqueante).toList();
        if (!bloqueantes.isEmpty()) {
            throw new ContenidoInvalidoException(
                    "No se pudo publicar: revisa los avisos del lienzo y de las reglas.", bloqueantes);
        }
        Instant ahora = Instant.now();
        CompositorGrafoClinico.Contenido contenido = publicador.publicar(b, ahora);
        b.setEstado(EstadoBorrador.PUBLICADO);
        b.setPublicadoEn(ahora);
        b.setCopiaPublicada(new CopiaPublicada(b.getDocumento(), b.getNombre(), b.getPuenteId(),
                b.getCategoria(), b.getEtiquetaOpcion(), b.getAutor(), b.getInstitucion()));
        b.setEnviadoRevisionEn(null);
        b.setRevisorId(null);
        b.setRevisadoEn(null);
        b.setComentarioRevision(null);
        BorradorSubgrafoEntity guardado = borradores.save(b);
        Optional<BorradorBaseReglaEntity> base = basesReglas.findByGrafoId(b.getGrafoId());
        if (base.isPresent()) {
            BorradorBaseReglaEntity baseRegla = base.get();
            contenido = publicadorRegla.publicar(baseRegla, ahora);
            baseRegla.setEstado(EstadoBorrador.PUBLICADO);
            baseRegla.setPublicadoEn(ahora);
            baseRegla.setCopiaPublicada(new CopiaBaseReglaPublicada(baseRegla.getDocumento(),
                    baseRegla.getNombre(), baseRegla.getDescripcion(), baseRegla.getAutor(),
                    baseRegla.getInstitucion()));
            basesReglas.save(baseRegla);
        }
        Long version = versiones.registrar(contenido.grafo(), contenido.reglas(),
                "Publicación de '" + b.getGrafoId() + "'");
        log.info("Subgrafo '{}' publicado con una versión nueva ({}).", b.getGrafoId(), version);
        PublicacionContenido.despuesDelCommit(imagenes::invalidarCachePublicadas);
        return dto(guardado);
    }

    @Override
    @Transactional
    public SubgrafoDTO despublicar(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        if (b.getPublicadoEn() == null) {
            throw new EdicionInvalidaException("Este subgrafo no está publicado, así que no hay nada que "
                    + "despublicar.");
        }
        publicador.retirar(b);
        basesReglas.findByGrafoId(b.getGrafoId()).ifPresent(base -> {
            base.setEstado(EstadoBorrador.BORRADOR);
            base.setPublicadoEn(null);
            base.setCopiaPublicada(null);
            base.setActualizadoEn(Instant.now());
            basesReglas.save(base);
        });
        b.setEstado(EstadoBorrador.BORRADOR);
        b.setPublicadoEn(null);
        b.setCopiaPublicada(null);
        b.setActualizadoEn(Instant.now());
        PublicacionContenido.despuesDelCommit(imagenes::invalidarCachePublicadas);
        return dto(borradores.save(b));
    }

    @Override
    @Transactional
    public SubgrafoDTO descartarCambios(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        if (b.getPublicadoEn() == null) {
            throw new EdicionInvalidaException("Este subgrafo todavía no se ha publicado, así que no hay una "
                    + "versión publicada a la que volver. Si ya no lo quieres, puedes eliminarlo.");
        }
        if (b.getEstado() == EstadoBorrador.PUBLICADO) {
            return dto(b);
        }
        CopiaPublicada copia = b.getCopiaPublicada();
        if (copia == null) {
            throw new EdicionInvalidaException("Este subgrafo se publicó antes de que se guardara una copia de "
                    + "lo publicado. Podrás descartar cambios a partir de su próxima publicación.");
        }
        Set<String> antes = imagenesDe(b.getDocumento());
        b.setDocumento(copia.documento());
        b.setNombre(copia.nombre());
        b.setPuenteId(copia.puenteId());
        b.setCategoria(copia.categoria());
        b.setEtiquetaOpcion(copia.etiquetaOpcion());
        b.setAutor(copia.autor());
        b.setInstitucion(copia.institucion());
        b.setEstado(EstadoBorrador.PUBLICADO);
        b.setActualizadoEn(Instant.now());
        SubgrafoDTO dto = dto(borradores.save(b));
        programarBorrado(antes, imagenesDe(b.getDocumento()));
        return dto;
    }

    /** Aplica una operación del editor al lienzo y guarda. */
    private SubgrafoDTO editar(Long id, UnaryOperator<DocumentoLienzo> operacion) {
        BorradorSubgrafoEntity b = buscar(id);
        b.setDocumento(operacion.apply(b.getDocumento()));
        marcarEditado(b);
        return dto(borradores.save(b));
    }

    private BorradorSubgrafoEntity buscar(Long id) {
        return borradores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay un subgrafo con id " + id));
    }

    /** Imágenes referenciadas por los nodos de un documento del lienzo. */
    private static Set<String> imagenesDe(DocumentoLienzo documento) {
        if (documento == null) {
            return Set.of();
        }
        Set<String> imagenes = new HashSet<>();
        for (NodoLienzo nodo : documento.nodos().values()) {
            imagenes.addAll(nodo.imagenes());
        }
        return imagenes;
    }

    /**
     * Programa borrar de Silo las imágenes que dejaron de estar en el nodo. La
     * comprobación de referencias se hace después del commit para que vea el
     * documento nuevo y no borre una imagen que otra pregunta o versión usa.
     */
    private void programarBorrado(Set<String> antes, Set<String> despues) {
        Set<String> retiradas = new HashSet<>(antes);
        retiradas.removeAll(despues);
        if (!retiradas.isEmpty()) {
            PublicacionContenido.despuesDelCommit(() -> imagenes.eliminarSiHuerfanas(retiradas));
        }
    }

    private static void marcarEditado(BorradorSubgrafoEntity b) {
        b.setActualizadoEn(Instant.now());
        if (b.getEstado() == EstadoBorrador.PUBLICADO) {
            b.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        }
    }

    /**
     * Avisos del lienzo más los de sus reglas: usa el borrador de reglas del
     * grafo si existe; si no, la base de reglas ya publicada. Detecta reglas que
     * apuntan a nodos o respuestas que el lienzo ya no tiene.
     */
    private List<Aviso> avisosDe(BorradorSubgrafoEntity b) {
        List<Aviso> avisos = new ArrayList<>(ValidadorLienzo.validar(b.getDocumento()));
        var borradorReglas = basesReglas.findByGrafoId(b.getGrafoId());
        if (borradorReglas.isPresent()) {
            avisos.addAll(ValidadorLienzoReglas.validar(b.getDocumento(),
                    borradorReglas.get().getDocumento()));
        } else {
            basesPublicadas.findAll().stream()
                    .map(fila -> fila.getEstructura())
                    .filter(e -> e != null && b.getGrafoId().equals(e.grafoId()))
                    .findFirst()
                    .ifPresent(e -> avisos.addAll(
                            ValidadorLienzoReglas.validarPublicada(b.getDocumento(), e)));
        }
        return avisos;
    }

    /** DTO del borrador con los avisos del lienzo y de las reglas. */
    private SubgrafoDTO dto(BorradorSubgrafoEntity b) {
        return SubgrafoDTO.desde(b, avisosDe(b));
    }

    /**
     * Valida y copia los datos del subgrafo; la categoría se toma de la
     * pregunta puente. El autor no se toca: sale de la cuenta al crear.
     */
    private void aplicarDatos(BorradorSubgrafoEntity b, SubgrafoRequest datos) {
        String nombre = datos == null ? null : limpio(datos.getNombre());
        if (nombre == null) {
            throw new EdicionInvalidaException("El subgrafo necesita un nombre.");
        }
        CategoriaDTO categoria = categorias().stream()
                .filter(c -> c.puenteId().equals(datos.getPuenteId()))
                .findFirst()
                .orElseThrow(() -> new EdicionInvalidaException("Elige una de las categorías del grafo raíz."));
        String etiqueta = limpio(datos.getEtiquetaOpcion());
        if (etiqueta == null) {
            throw new EdicionInvalidaException("Escribe cómo verán este signo las familias, con palabras "
                    + "sencillas (por ejemplo «Piel u ojos amarillos»).");
        }
        b.setNombre(nombre);
        b.setPuenteId(categoria.puenteId());
        b.setCategoria(categoria.nombre());
        b.setEtiquetaOpcion(etiqueta);
    }

    /** Traduce la petición a los datos del editor. */
    private static EditorLienzo.DatosNodo datosDe(NodoRequest nodo) {
        if (nodo == null) {
            return null;
        }
        List<EditorLienzo.DatosRespuesta> respuestas = new ArrayList<>();
        if (nodo.getRespuestas() != null) {
            for (OpcionRequest o : nodo.getRespuestas()) {
                if (o != null) {
                    respuestas.add(new EditorLienzo.DatosRespuesta(o.getId(), o.getTexto(), o.getGravedad()));
                }
            }
        }
        return new EditorLienzo.DatosNodo(nodo.getTipo(), nodo.getTexto(),
                Boolean.TRUE.equals(nodo.getVariasRespuestas()), nodo.getDesenlace(), respuestas,
                nodo.getImagenes());
    }

    /**
     * {@code grafo_<slug>} a partir del nombre, sin tildes ni símbolos, que no
     * use ningún borrador ni grafo guardado. Si está ocupado se le agrega
     * {@code _2}, {@code _3}…
     */
    private String grafoIdLibre(String nombre) {
        String slug = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.length() > LARGO_SLUG) {
            slug = slug.substring(0, LARGO_SLUG).replaceAll("_+$", "");
        }
        if (slug.isEmpty()) {
            slug = "subgrafo";
        }
        Set<String> ocupados = new HashSet<>();
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            GrafoClinicoJson e = fila.getEstructura();
            if (e != null && e.graph() != null && e.graph().id() != null) {
                ocupados.add(e.graph().id());
            }
        }
        ocupados.add(CompositorGrafoClinico.ID_RAIZ);
        String base = "grafo_" + slug;
        String candidato = base;
        for (int i = 2; ocupados.contains(candidato) || borradores.existsByGrafoId(candidato); i++) {
            candidato = base + "_" + i;
        }
        return candidato;
    }

    /** Grafo raíz guardado. */
    private GrafoClinicoJson.Grafo raiz() {
        return grafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .filter(e -> e != null && e.graph() != null
                        && CompositorGrafoClinico.ID_RAIZ.equals(e.graph().id()))
                .map(GrafoClinicoJson::graph)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no hay grafo raíz en la base de datos"));
    }

    /** Texto sin espacios de los bordes, o {@code null} si queda vacío. */
    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
