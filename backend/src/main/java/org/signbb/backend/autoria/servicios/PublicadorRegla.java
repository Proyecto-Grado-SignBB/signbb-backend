package org.signbb.backend.autoria.servicios;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.modelo.Aviso;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica un borrador de base de reglas: lo deja activo para la cuidadora.
 *
 * <ol>
 * <li>Rechaza el borrador si tiene avisos bloqueantes.</li>
 * <li>Verifica que el grafo destino ({@code grafoId}) esté publicado.</li>
 * <li>Lo compila con {@link CompiladorReglas}.</li>
 * <li>Reemplaza la base de ese grafo en el conjunto de bases y compone con los
 * subgrafos y todas las bases, reconstruyendo los TAD
 * ({@code GrafoClinicoMapper}, {@code BaseReglasMapper}). Si algo falla no se
 * guarda nada.</li>
 * <li>Guarda (o actualiza) la fila de la base de reglas de ese grafo.</li>
 * <li>Devuelve el contenido compuesto; la publicación completa registra una
 * sola versión con él y avisa al cache cuando la transacción confirma.</li>
 * </ol>
 *
 * <p>
 * Mismo contrato que {@link PublicadorSubgrafo}: recibe el borrador y la fecha,
 * no cambia el estado (lo hace quien llama, dentro de la misma transacción).
 */
@Component
public class PublicadorRegla {

    private static final Logger log = LoggerFactory.getLogger(PublicadorRegla.class);

    private final RepositorioGrafoClinico grafos;
    private final RepositorioBaseReglas bases;
    private final ResolvedorContenidoClinico resolvedor;
    private final ContextoClinico contexto;

    /** Inyecta los repositorios del contenido clínico, el cache y el contexto en memoria. */
    public PublicadorRegla(RepositorioGrafoClinico grafos, RepositorioBaseReglas bases,
            ResolvedorContenidoClinico resolvedor, ContextoClinico contexto) {
        this.grafos = grafos;
        this.bases = bases;
        this.resolvedor = resolvedor;
        this.contexto = contexto;
    }

    /**
     * Publica el borrador. No cambia su estado: eso lo hace quien llama,
     * dentro de la misma transacción.
     *
     * @return el grafo y las reglas ya compuestos, para registrar una sola versión
     * @throws ContenidoInvalidoException si hay avisos bloqueantes, si el grafo
     *                                    destino no está publicado o el
     *                                    contenido compuesto no es válido
     */
    @Transactional
    public CompositorGrafoClinico.Contenido publicar(BorradorBaseReglaEntity borrador, Instant fecha) {
        if (PoliticaAccesoBorradores.esBaseRaiz(borrador.getGrafoId())) {
            throw new EdicionInvalidaException(PoliticaAccesoBorradores.REGLAS_RAIZ_SIN_BASE);
        }
        List<Aviso> bloqueantes = ValidadorReglas.validar(borrador.getDocumento()).stream()
                .filter(Aviso::bloqueante)
                .toList();
        if (!bloqueantes.isEmpty()) {
            throw new ContenidoInvalidoException("Antes de publicar hay que resolver los avisos.", bloqueantes);
        }

        GrafoClinicoJson raiz = null;
        List<GrafoClinicoJson> subgrafos = new ArrayList<>();
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            String id = idDe(fila.getEstructura());
            if (id == null) {
                continue;
            }
            if (CompositorGrafoClinico.ID_RAIZ.equals(id)) {
                raiz = fila.getEstructura();
            } else {
                subgrafos.add(fila.getEstructura());
            }
        }
        if (raiz == null) {
            throw new IllegalStateException("no hay grafo raíz ('" + CompositorGrafoClinico.ID_RAIZ
                    + "') en la base de datos");
        }
        if (!existeGrafo(borrador.getGrafoId())) {
            throw new ContenidoInvalidoException("El grafo \"" + borrador.getGrafoId()
                    + "\" todavía no está publicado: publica el subgrafo antes que sus reglas.", List.of());
        }

        BaseReglasJson compilada = CompiladorReglas.compilar(borrador, fecha);

        BaseReglasEntity filaBase = null;
        List<BaseReglasJson> documentos = new ArrayList<>();
        for (BaseReglasEntity fila : bases.findAll()) {
            BaseReglasJson documento = fila.getEstructura();
            if (documento == null || documento.grafoId() == null || documento.grafoId().isBlank()) {
                continue;
            }
            if (documento.grafoId().equals(borrador.getGrafoId())) {
                filaBase = fila;
            } else {
                documentos.add(documento);
            }
        }
        documentos.add(compilada);
        CompositorGrafoClinico.Contenido contenido = PublicacionContenido.validarComposicion(
                "La base de reglas no se pudo publicar", raiz, subgrafos, documentos);

        if (filaBase == null) {
            filaBase = new BaseReglasEntity();
        }
        filaBase.setEstructura(compilada);
        bases.save(filaBase);
        log.info("Base de reglas '{}' publicada para '{}' con {} regla(s).",
                borrador.getNombre(), borrador.getGrafoId(), compilada.rules().size());

        PublicacionContenido.despuesDelCommit(() -> {
            resolvedor.invalidar();
            contexto.recargar();
        });
        return contenido;
    }

    /** True si hay un grafo publicado con ese id. */
    private boolean existeGrafo(String grafoId) {
        return grafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .anyMatch(documento -> grafoId.equals(idDe(documento)));
    }

    private static String idDe(GrafoClinicoJson documento) {
        if (documento == null || documento.graph() == null) {
            return null;
        }
        String id = documento.graph().id();
        return id == null || id.isBlank() ? null : id;
    }
}
