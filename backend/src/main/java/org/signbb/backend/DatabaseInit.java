package org.signbb.backend;

import java.util.List;

import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Inicialización de la base de datos: siembra el contenido clínico
 * ({@link ContenidoEjemplo}: grafo raíz, los subgrafos de respiración,
 * gastrointestinal y actividad/color, cada uno con su base de reglas) cuando
 * aún no existe el grafo raíz.
 *
 * <p>
 * Antes de guardar se compone el contenido y se reconstruyen los TAD, de modo
 * que solo se persisten datos válidos (pesos fuera de 1–3, referencias
 * colgantes, niveles inválidos o aristas rotas fallan aquí). Cada documento se
 * guarda por separado y tal cual viene del JSON, para conservar los metadatos
 * de presentación (autor, categoría) que el TAD no almacena; la composición se
 * repite al resolver el contenido. La siembra es idempotente: si el grafo raíz
 * ya está, no hace nada. Las filas del formato previo no se tocan; el
 * resolvedor las ignora.
 *
 * <p>
 * MODIFICADO (1.6): solo existe si {@code signbb.siembra.activa} vale
 * {@code true} ({@code SIGNBB_SIEMBRA_ACTIVA}, encendida por defecto para
 * desarrollo y CI). En producción se enciende en el primer despliegue y luego
 * se apaga.
 */
@Component
@ConditionalOnProperty(name = "signbb.siembra.activa", havingValue = "true")
public class DatabaseInit implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInit.class);

    private final RepositorioGrafoClinico repositorioGrafos;
    private final RepositorioBaseReglas repositorioBases;

    /** Inyecta los repositorios de grafos clínicos y bases de reglas. */
    public DatabaseInit(RepositorioGrafoClinico repositorioGrafos,
            RepositorioBaseReglas repositorioBases) {
        this.repositorioGrafos = repositorioGrafos;
        this.repositorioBases = repositorioBases;
    }

    /** Siembra el contenido de ejemplo si el grafo raíz no existe. */
    @Override
    public void run(String... args) {
        boolean hayRaiz = repositorioGrafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .anyMatch(DatabaseInit::esRaiz);
        if (hayRaiz) {
            log.info("Base de datos ya inicializada: {} grafo(s), {} base(s) de reglas.",
                    repositorioGrafos.count(), repositorioBases.count());
            return;
        }

        GrafoClinicoJson raiz = ContenidoEjemplo.raiz();
        List<GrafoClinicoJson> subgrafos = ContenidoEjemplo.subgrafos();
        List<BaseReglasJson> bases = ContenidoEjemplo.bases();
        validar(CompositorGrafoClinico.componer(raiz, subgrafos, bases));

        guardarGrafo(raiz);
        subgrafos.forEach(this::guardarGrafo);
        bases.forEach(this::guardarBase);
        log.info("Contenido clínico de ejemplo sembrado: 1 grafo raíz, {} subgrafo(s), "
                + "{} base(s) de reglas.", subgrafos.size(), bases.size());
    }

    /** Reconstruye los TAD del contenido compuesto y avisa de rutas dudosas. */
    private static void validar(CompositorGrafoClinico.Contenido contenido) {
        GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
        BaseReglas base = BaseReglasMapper.desde(contenido.reglas(), grafo);
        if (!grafo.rutaValida()) {
            log.warn("El grafo sembrado no cumple rutaValida(): puede haber nodos huérfanos "
                    + "o preguntas sin salida.");
        }
        if (!grafo.validarCiclo()) {
            log.warn("El grafo sembrado contiene ciclos: la navegación podría no terminar.");
        }
        log.info("Contenido de ejemplo validado: {} nodos y {} reglas tras componer.",
                grafo.size(), base.size());
    }

    private void guardarGrafo(GrafoClinicoJson documento) {
        GrafoClinicoEntity entidad = new GrafoClinicoEntity();
        entidad.setEstructura(documento);
        repositorioGrafos.save(entidad);
    }

    private void guardarBase(BaseReglasJson documento) {
        BaseReglasEntity entidad = new BaseReglasEntity();
        entidad.setEstructura(documento);
        repositorioBases.save(entidad);
    }

    /** True si el documento es el grafo raíz del sistema. */
    private static boolean esRaiz(GrafoClinicoJson documento) {
        return documento != null && documento.graph() != null
                && CompositorGrafoClinico.ID_RAIZ.equals(documento.graph().id());
    }
}
