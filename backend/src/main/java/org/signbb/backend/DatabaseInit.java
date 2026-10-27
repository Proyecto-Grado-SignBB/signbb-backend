package org.signbb.backend;

import java.util.List;

import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.signbb.backend.reglas.modelo.BaseReglas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Inicialización de la base de datos: siembra solo el grafo raíz del sistema
 * ({@link ContenidoEjemplo#raiz()}) cuando aún no existe. Los subgrafos y sus
 * bases de reglas ya no se cargan automáticamente: se crean y publican desde
 * el módulo de autoría (o con el seed de datos que se defina para las pruebas
 * y la demo).
 *
 * <p>
 * Antes de guardar se compone el contenido y se reconstruyen los TAD, de modo
 * que solo se persisten datos válidos. La siembra es idempotente: si el grafo
 * raíz ya está, no hace nada. Como la base de reglas del grafo raíz es del
 * sistema y no se siembra por defecto, el contenido inicial queda sin bases;
 * el resolvedor devuelve una base vacía hasta que se publique un subgrafo.
 *
 * <p>
 * MODIFICADO (1.6): solo existe si {@code signbb.siembra.activa} vale
 * {@code true} ({@code SIGNBB_SIEMBRA_ACTIVA}, encendida por defecto para
 * desarrollo y CI). En producción se enciende en el primer despliegue y luego
 * se apaga.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "signbb.siembra.activa", havingValue = "true")
public class DatabaseInit implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInit.class);

    private final RepositorioGrafoClinico repositorioGrafos;
    private final ResolvedorContenidoClinico resolvedor;

    /** Inyecta el repositorio de grafos y el resolvedor del cache de contenido. */
    public DatabaseInit(RepositorioGrafoClinico repositorioGrafos, ResolvedorContenidoClinico resolvedor) {
        this.repositorioGrafos = repositorioGrafos;
        this.resolvedor = resolvedor;
    }

    /** Siembra el grafo raíz si no existe. */
    @Override
    public void run(String... args) {
        boolean hayRaiz = repositorioGrafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .anyMatch(DatabaseInit::esRaiz);
        if (hayRaiz) {
            log.info("Base de datos ya inicializada: {} grafo(s).", repositorioGrafos.count());
            return;
        }

        GrafoClinicoJson raiz = ContenidoEjemplo.raiz();
        validar(CompositorGrafoClinico.componer(raiz, List.of(), List.of()));

        guardarGrafo(raiz);
        // Si Redis conserva contenido de una base anterior, se descarta para que
        // la próxima resolución lea el grafo raíz recién sembrado.
        resolvedor.invalidar();
        log.info("Contenido clínico inicial sembrado: solo el grafo raíz, sin subgrafos ni bases de reglas.");
    }

    /** Reconstruye los TAD del contenido inicial y avisa de rutas dudosas. */
    private static void validar(CompositorGrafoClinico.Contenido contenido) {
        GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
        BaseReglas base = BaseReglasMapper.desde(contenido.reglas(), grafo);
        boolean disponible = grafo.obtenerNodoInicial()
                .map(nodo -> grafo.esNodoTerminal(nodo) || !grafo.obtenerOpciones(nodo).isEmpty())
                .orElse(false);
        if (!disponible) {
            log.info("El grafo raíz se sembró sin subgrafos publicados: la consulta queda no "
                    + "disponible hasta que se publique el primero.");
        } else if (!grafo.rutaValida()) {
            log.warn("El grafo sembrado no cumple rutaValida(): puede haber nodos huérfanos "
                    + "o preguntas sin salida.");
        }
        if (!grafo.validarCiclo()) {
            log.warn("El grafo sembrado contiene ciclos: la navegación podría no terminar.");
        }
        log.info("Contenido inicial validado: {} nodos y {} reglas.", grafo.size(), base.size());
    }

    private void guardarGrafo(GrafoClinicoJson documento) {
        GrafoClinicoEntity entidad = new GrafoClinicoEntity();
        entidad.setEstructura(documento);
        repositorioGrafos.save(entidad);
    }

    /** True si el documento es el grafo raíz del sistema. */
    private static boolean esRaiz(GrafoClinicoJson documento) {
        return documento != null && documento.graph() != null
                && CompositorGrafoClinico.ID_RAIZ.equals(documento.graph().id());
    }
}
