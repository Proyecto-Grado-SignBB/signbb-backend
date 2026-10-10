package org.signbb.backend.autoria.servicios;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.signbb.backend.autoria.controladores.CategoriaDTO;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.OpcionRequest;
import org.signbb.backend.autoria.controladores.ResumenSubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
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

    /** Largo máximo del slug del {@code grafoId}. */
    private static final int LARGO_SLUG = 40;

    private final RepositorioBorradorSubgrafo borradores;
    private final RepositorioGrafoClinico grafos;
    private final PublicadorSubgrafo publicador;

    /** Inyecta los repositorios y el publicador. */
    public ServicioAutoriaImpl(RepositorioBorradorSubgrafo borradores, RepositorioGrafoClinico grafos,
            PublicadorSubgrafo publicador) {
        this.borradores = borradores;
        this.grafos = grafos;
        this.publicador = publicador;
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
    public List<ResumenSubgrafoDTO> listar() {
        return borradores.findAll(Sort.by(Sort.Direction.DESC, "actualizadoEn")).stream()
                .map(ResumenSubgrafoDTO::desde)
                .toList();
    }

    @Override
    @Transactional
    public SubgrafoDTO crear(SubgrafoRequest datos) {
        BorradorSubgrafoEntity b = new BorradorSubgrafoEntity();
        aplicarDatos(b, datos);
        b.setGrafoId(grafoIdLibre(b.getNombre()));
        b.setDocumento(DocumentoLienzo.vacio());
        b.setEstado(EstadoBorrador.BORRADOR);
        Instant ahora = Instant.now();
        b.setCreadoEn(ahora);
        b.setActualizadoEn(ahora);
        return SubgrafoDTO.desde(borradores.save(b));
    }

    @Override
    @Transactional(readOnly = true)
    public SubgrafoDTO obtener(Long id) {
        return SubgrafoDTO.desde(buscar(id));
    }

    @Override
    @Transactional
    public SubgrafoDTO actualizar(Long id, SubgrafoRequest datos) {
        BorradorSubgrafoEntity b = buscar(id);
        aplicarDatos(b, datos);
        marcarEditado(b);
        return SubgrafoDTO.desde(borradores.save(b));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        if (b.getPublicadoEn() != null) {
            throw new EdicionInvalidaException(
                    "Este subgrafo ya está publicado y las cuidadoras lo usan: no se puede borrar.");
        }
        borradores.delete(b);
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
        return editar(id, d -> EditorLienzo.editarNodo(d, nodoId, datosDe(nodo)));
    }

    @Override
    @Transactional
    public SubgrafoDTO eliminarNodo(Long id, String nodoId) {
        return editar(id, d -> EditorLienzo.eliminarNodo(d, nodoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Aviso> avisos(Long id) {
        return ValidadorLienzo.validar(buscar(id).getDocumento());
    }

    @Override
    @Transactional
    public SubgrafoDTO publicar(Long id) {
        BorradorSubgrafoEntity b = buscar(id);
        Instant ahora = Instant.now();
        publicador.publicar(b, ahora);
        b.setEstado(EstadoBorrador.PUBLICADO);
        b.setPublicadoEn(ahora);
        return SubgrafoDTO.desde(borradores.save(b));
    }

    /** Aplica una operación del editor al lienzo y guarda. */
    private SubgrafoDTO editar(Long id, UnaryOperator<DocumentoLienzo> operacion) {
        BorradorSubgrafoEntity b = buscar(id);
        b.setDocumento(operacion.apply(b.getDocumento()));
        marcarEditado(b);
        return SubgrafoDTO.desde(borradores.save(b));
    }

    private BorradorSubgrafoEntity buscar(Long id) {
        return borradores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay un subgrafo con id " + id));
    }

    private static void marcarEditado(BorradorSubgrafoEntity b) {
        b.setActualizadoEn(Instant.now());
        if (b.getEstado() == EstadoBorrador.PUBLICADO) {
            b.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        }
    }

    /** Valida y copia los datos del subgrafo; la categoría se toma de la pregunta puente. */
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
        b.setAutor(limpio(datos.getAutor()));
        b.setInstitucion(limpio(datos.getInstitucion()));
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
                Boolean.TRUE.equals(nodo.getVariasRespuestas()), nodo.getDesenlace(), respuestas);
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
