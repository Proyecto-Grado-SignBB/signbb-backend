package org.signbb.backend.autoria.servicios;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica un borrador: lo deja navegable para la cuidadora.
 *
 * <ol>
 * <li>Rechaza el borrador si tiene avisos bloqueantes.</li>
 * <li>Lo compila con {@link CompiladorSubgrafo}.</li>
 * <li>Arma el grafo raíz nuevo: un nodo {@code referencia}
 * {@code ref_<grafoId>} y la arista
 * {@code puente --etiquetaOpcion--> ref_<grafoId>}. Al republicar se
 * reemplaza la arista anterior, así que la referencia no se duplica.</li>
 * <li>Compone la raíz nueva con los demás subgrafos y todas las bases de
 * reglas y reconstruye los TAD ({@link GrafoClinicoMapper},
 * {@link BaseReglasMapper}), igual que {@code ContextoClinico}. Si algo
 * falla no se guarda nada.</li>
 * <li>Guarda (o actualiza) la fila del subgrafo y la del grafo raíz, y una
 * versión nueva e inmutable del grafo compuesto ({@link ResolvedorVersiones}).</li>
 * <li>Cuando la transacción confirma, invalida el cache de Redis y hace que
 * {@link ContextoClinico} vuelva a resolver el contenido.</li>
 * </ol>
 *
 * <p>
 * Los recorridos en curso no se ven afectados: cada uno está atado a la
 * versión con la que empezó y la termina con ella; los recorridos nuevos
 * usan la versión recién publicada.
 */
@Component
public class PublicadorSubgrafo {

    private static final Logger log = LoggerFactory.getLogger(PublicadorSubgrafo.class);

    /** Prefijo del nodo {@code referencia} de un subgrafo en el grafo raíz. */
    public static final String PREFIJO_REFERENCIA = "ref_";

    private final RepositorioGrafoClinico grafos;
    private final RepositorioBaseReglas bases;
    private final ResolvedorContenidoClinico resolvedor;
    private final ResolvedorVersiones versiones;
    private final ContextoClinico contexto;

    /** Inyecta los repositorios del contenido clínico, el cache, las versiones y el contexto en memoria. */
    public PublicadorSubgrafo(RepositorioGrafoClinico grafos, RepositorioBaseReglas bases,
            ResolvedorContenidoClinico resolvedor, ResolvedorVersiones versiones, ContextoClinico contexto) {
        this.grafos = grafos;
        this.bases = bases;
        this.resolvedor = resolvedor;
        this.versiones = versiones;
        this.contexto = contexto;
    }

    /**
     * Publica el borrador. No cambia su estado: eso lo hace quien llama,
     * dentro de la misma transacción.
     *
     * @throws ContenidoInvalidoException si hay avisos bloqueantes o el
     *                                    contenido compuesto no es válido
     */
    @Transactional
    public void publicar(BorradorSubgrafoEntity borrador, Instant fecha) {
        List<Aviso> bloqueantes = ValidadorLienzo.validar(borrador.getDocumento()).stream()
                .filter(Aviso::bloqueante)
                .toList();
        if (!bloqueantes.isEmpty()) {
            throw new ContenidoInvalidoException("Antes de publicar hay que resolver los avisos.", bloqueantes);
        }
        GrafoClinicoJson subgrafo = CompiladorSubgrafo.compilar(borrador, fecha);

        GrafoClinicoEntity filaRaiz = null;
        GrafoClinicoEntity filaSubgrafo = null;
        List<GrafoClinicoJson> subgrafos = new ArrayList<>();
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            String id = idDe(fila.getEstructura());
            if (id == null) {
                continue;
            }
            if (CompositorGrafoClinico.ID_RAIZ.equals(id)) {
                filaRaiz = fila;
            } else if (id.equals(borrador.getGrafoId())) {
                filaSubgrafo = fila;
            } else {
                subgrafos.add(fila.getEstructura());
            }
        }
        if (filaRaiz == null) {
            throw new IllegalStateException("no hay grafo raíz ('" + CompositorGrafoClinico.ID_RAIZ
                    + "') en la base de datos");
        }
        GrafoClinicoJson raiz = conReferencia(filaRaiz.getEstructura(), borrador);
        subgrafos.add(subgrafo);
        CompositorGrafoClinico.Contenido compuesto = PublicacionContenido.validarComposicion(
                "El subgrafo no se pudo publicar", raiz, subgrafos, reglas());

        if (filaSubgrafo == null) {
            filaSubgrafo = new GrafoClinicoEntity();
        }
        filaSubgrafo.setEstructura(subgrafo);
        grafos.save(filaSubgrafo);
        filaRaiz.setEstructura(raiz);
        grafos.save(filaRaiz);
        Long version = versiones.registrar(compuesto.grafo(), compuesto.reglas(),
                "Publicación de '" + borrador.getGrafoId() + "'");
        log.info("Subgrafo '{}' publicado en '{}' con {} nodos (versión {} del contenido).",
                borrador.getGrafoId(), borrador.getPuenteId(), subgrafo.graph().nodes().size(), version);

        PublicacionContenido.despuesDelCommit(() -> {
            resolvedor.invalidar();
            contexto.recargar();
        });
    }

    /**
     * Retira un subgrafo publicado: quita su referencia y sus opciones del grafo
     * raíz y borra su fila, de modo que las cuidadoras que empiecen un recorrido
     * nuevo ya no lo ven. Los recorridos en curso terminan con la versión con la
     * que empezaron. Si el contenido que queda no es válido (por ejemplo, era el
     * único subgrafo publicado), no se guarda nada.
     *
     * @param borrador borrador del subgrafo que se retira
     * @throws ContenidoInvalidoException si el contenido restante no se puede componer
     */
    @Transactional
    public void retirar(BorradorSubgrafoEntity borrador) {
        GrafoClinicoEntity filaRaiz = null;
        GrafoClinicoEntity filaSubgrafo = null;
        List<GrafoClinicoJson> restantes = new ArrayList<>();
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            String id = idDe(fila.getEstructura());
            if (id == null) {
                continue;
            }
            if (CompositorGrafoClinico.ID_RAIZ.equals(id)) {
                filaRaiz = fila;
            } else if (id.equals(borrador.getGrafoId())) {
                filaSubgrafo = fila;
            } else {
                restantes.add(fila.getEstructura());
            }
        }
        if (filaRaiz == null) {
            throw new IllegalStateException("no hay grafo raíz ('" + CompositorGrafoClinico.ID_RAIZ
                    + "') en la base de datos");
        }
        if (restantes.isEmpty()) {
            throw new ContenidoInvalidoException("Este es el único subgrafo publicado: si se despublica, la "
                    + "app de las cuidadoras se quedaría sin nada que mostrar. Publica otro antes.", List.of());
        }
        GrafoClinicoJson raiz = sinReferencia(filaRaiz.getEstructura(), borrador.getGrafoId());
        CompositorGrafoClinico.Contenido compuesto = PublicacionContenido.validarComposicion(
                "El subgrafo no se pudo despublicar", raiz, restantes, reglas());

        filaRaiz.setEstructura(raiz);
        grafos.save(filaRaiz);
        if (filaSubgrafo != null) {
            grafos.delete(filaSubgrafo);
        }
        Long version = versiones.registrar(compuesto.grafo(), compuesto.reglas(),
                "Despublicación de '" + borrador.getGrafoId() + "'");
        log.info("Subgrafo '{}' despublicado (versión {} del contenido).", borrador.getGrafoId(), version);

        PublicacionContenido.despuesDelCommit(() -> {
            resolvedor.invalidar();
            contexto.recargar();
        });
    }

    /** Copia del grafo raíz sin el nodo de referencia al subgrafo ni las aristas que lo tocan. */
    static GrafoClinicoJson sinReferencia(GrafoClinicoJson raiz, String grafoId) {
        GrafoClinicoJson.Grafo grafo = raiz.graph();
        Map<String, GrafoClinicoJson.NodoJson> nodos = new LinkedHashMap<>();
        List<String> quitados = new ArrayList<>();
        for (Map.Entry<String, GrafoClinicoJson.NodoJson> e : grafo.nodes().entrySet()) {
            GrafoClinicoJson.MetadataNodo m = e.getValue().metadata();
            if (m != null && "referencia".equals(m.tipo()) && grafoId.equals(m.grafoId())) {
                quitados.add(e.getKey());
            } else {
                nodos.put(e.getKey(), e.getValue());
            }
        }
        List<GrafoClinicoJson.Arista> aristas = new ArrayList<>();
        for (GrafoClinicoJson.Arista a : grafo.edges() == null ? List.<GrafoClinicoJson.Arista>of() : grafo.edges()) {
            if (!quitados.contains(a.source()) && !quitados.contains(a.target())) {
                aristas.add(a);
            }
        }
        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo(grafo.id(), grafo.label(), grafo.directed(),
                grafo.category(), grafo.preguntaInicial(), grafo.nodoInicial(), grafo.metadata(), nodos, aristas));
    }

    /**
     * Copia del grafo raíz con la referencia al subgrafo y la arista desde su
     * pregunta puente. Las aristas que ya llegaban a la referencia se
     * reemplazan, por si cambió la pregunta puente o el texto de la opción.
     *
     * @throws ContenidoInvalidoException si la pregunta puente no existe o ya
     *                                    tiene otra opción con el mismo texto
     */
    static GrafoClinicoJson conReferencia(GrafoClinicoJson raiz, BorradorSubgrafoEntity borrador) {
        GrafoClinicoJson.Grafo grafo = raiz.graph();
        String puente = borrador.getPuenteId();
        String referencia = PREFIJO_REFERENCIA + borrador.getGrafoId();
        String etiqueta = borrador.getEtiquetaOpcion();

        GrafoClinicoJson.NodoJson nodoPuente = grafo.nodes().get(puente);
        if (nodoPuente == null || nodoPuente.metadata() == null
                || !"pregunta".equals(nodoPuente.metadata().tipo())) {
            throw new ContenidoInvalidoException(
                    "La categoría elegida ya no existe en el grafo raíz: elige otra.", List.of());
        }
        GrafoClinicoJson.NodoJson existente = grafo.nodes().get(referencia);
        if (existente != null && (existente.metadata() == null
                || !borrador.getGrafoId().equals(existente.metadata().grafoId()))) {
            throw new IllegalStateException("el grafo raíz ya tiene un nodo '" + referencia
                    + "' que no apunta a '" + borrador.getGrafoId() + "'");
        }
        List<GrafoClinicoJson.Arista> aristas = new ArrayList<>();
        for (GrafoClinicoJson.Arista a : grafo.edges() == null ? List.<GrafoClinicoJson.Arista>of() : grafo.edges()) {
            if (referencia.equals(a.target())) {
                continue;
            }
            if (puente.equals(a.source()) && a.relation() != null
                    && EditorLienzo.normalizado(a.relation()).equals(EditorLienzo.normalizado(etiqueta))) {
                throw new ContenidoInvalidoException("\"" + nodoPuente.label() + "\" ya tiene la opción \""
                        + a.relation() + "\": cambia el texto de la opción del subgrafo.", List.of());
            }
            aristas.add(a);
        }
        aristas.add(new GrafoClinicoJson.Arista(puente, etiqueta, referencia, true));

        Map<String, GrafoClinicoJson.NodoJson> nodos = new LinkedHashMap<>(grafo.nodes());
        nodos.put(referencia, new GrafoClinicoJson.NodoJson(borrador.getNombre(),
                new GrafoClinicoJson.MetadataNodo("referencia", null, null, borrador.getGrafoId(),
                        GrafoClinicoMapper.PESO_MINIMO, false, List.of())));
        return new GrafoClinicoJson(new GrafoClinicoJson.Grafo(grafo.id(), grafo.label(), grafo.directed(),
                grafo.category(), grafo.preguntaInicial(), grafo.nodoInicial(), grafo.metadata(), nodos, aristas));
    }

    /** Bases de reglas guardadas; las del formato previo (sin {@code grafoId}) se ignoran. */
    private List<BaseReglasJson> reglas() {
        return bases.findAll().stream()
                .map(fila -> fila.getEstructura())
                .filter(b -> b != null && b.grafoId() != null && !b.grafoId().isBlank())
                .toList();
    }

    private static String idDe(GrafoClinicoJson documento) {
        if (documento == null || documento.graph() == null) {
            return null;
        }
        String id = documento.graph().id();
        return id == null || id.isBlank() ? null : id;
    }
}
