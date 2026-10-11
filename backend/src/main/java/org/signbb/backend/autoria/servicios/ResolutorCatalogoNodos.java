package org.signbb.backend.autoria.servicios;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.signbb.backend.autoria.controladores.CatalogoDTO;
import org.signbb.backend.autoria.controladores.CatalogoDTO.NodoCatalogoDTO;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.RespuestaLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.Respuesta;
import org.springframework.stereotype.Component;

/**
 * Arma el catálogo de nodos y respuestas de un grafo para el editor de reglas.
 *
 * <ul>
 * <li>Grafo raíz: los nodos del grafo compuesto (ids ya prefijados), lo que
 * permite reglas que combinan signos de varias categorías.</li>
 * <li>Subgrafo con borrador de lienzo: los nodos locales del lienzo, que es lo
 * que el médico está armando.</li>
 * <li>Subgrafo publicado sin lienzo: los nodos locales del grafo publicado.</li>
 * </ul>
 */
@Component
public class ResolutorCatalogoNodos {

    private final RepositorioBorradorSubgrafo subgrafos;
    private final RepositorioGrafoClinico grafos;
    private final ContextoClinico contexto;

    /** Inyecta los repositorios y el contexto clínico en memoria. */
    public ResolutorCatalogoNodos(RepositorioBorradorSubgrafo subgrafos, RepositorioGrafoClinico grafos,
            ContextoClinico contexto) {
        this.subgrafos = subgrafos;
        this.grafos = grafos;
        this.contexto = contexto;
    }

    /**
     * Catálogo del grafo indicado.
     *
     * @throws EdicionInvalidaException     si no se indicó el grafo
     * @throws RecursoNoEncontradoException si el grafo no existe
     */
    public CatalogoDTO catalogo(String grafoId) {
        if (grafoId == null || grafoId.isBlank()) {
            throw new EdicionInvalidaException("Elige el grafo al que pertenecen las reglas.");
        }
        if (CompositorGrafoClinico.ID_RAIZ.equals(grafoId)) {
            return desdeGrafo(grafoId, contexto.getGrafo());
        }
        Optional<BorradorSubgrafoEntity> borrador = subgrafos.findByGrafoId(grafoId);
        if (borrador.isPresent()) {
            return desdeLienzo(grafoId, borrador.get().getDocumento());
        }
        GrafoClinicoJson documento = grafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .filter(d -> d != null && d.graph() != null && grafoId.equals(d.graph().id()))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay un grafo '" + grafoId + "'"));
        return desdeGrafo(grafoId, GrafoClinicoMapper.desde(documento));
    }

    private static CatalogoDTO desdeGrafo(String grafoId, GrafoClinico grafo) {
        List<NodoCatalogoDTO> nodos = new ArrayList<>();
        for (Nodo nodo : grafo.obtenerNodos()) {
            List<String> opciones = nodo.esTerminal() ? List.of()
                    : grafo.obtenerOpciones(nodo).stream().map(Respuesta::getCondicion).toList();
            nodos.add(new NodoCatalogoDTO(nodo.getId(), nodo.getContenido(),
                    nodo.esTerminal() ? "terminal" : "pregunta", opciones));
        }
        return new CatalogoDTO(grafoId, nodos);
    }

    private static CatalogoDTO desdeLienzo(String grafoId, DocumentoLienzo documento) {
        List<NodoCatalogoDTO> nodos = new ArrayList<>();
        for (NodoLienzo nodo : documento.nodos().values()) {
            List<String> opciones = nodo.tipo() == TipoNodo.PREGUNTA
                    ? nodo.respuestas().stream().map(RespuestaLienzo::texto).toList()
                    : List.of();
            nodos.add(new NodoCatalogoDTO(nodo.id(), nodo.texto(),
                    nodo.tipo() == TipoNodo.PREGUNTA ? "pregunta" : "terminal", opciones));
        }
        return new CatalogoDTO(grafoId, nodos);
    }
}
