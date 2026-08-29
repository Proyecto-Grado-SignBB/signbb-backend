package org.signbb.backend.navegacion.servicios;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.repositorios.RepositorioGrafo;
import org.signbb.backend.navegacion.repositorios.RepositorioNodo;
import org.signbb.backend.navegacion.repositorios.RepositorioRecorrido;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicioNavegacionImpl implements ServicioNavegacion {

    private final RepositorioNodo repoNodo;
    private final RepositorioRecorrido repoRecorrido;
    private final RepositorioGrafo repoGrafo;

    @Autowired
    public ServicioNavegacionImpl(RepositorioNodo repoNodo,
                                  RepositorioRecorrido repoRecorrido,
                                  RepositorioGrafo repoGrafo) {
        this.repoNodo = repoNodo;
        this.repoRecorrido = repoRecorrido;
        this.repoGrafo = repoGrafo;
    }

    @Override
    public Nodo guardar(Nodo nodo) {
        return repoNodo.save(nodo);
    }

    @Override
    public Recorrido iniciar(long idGrafo) {
        Grafo grafo = repoGrafo.findById(idGrafo)
            .orElseThrow(() -> new RecursoNoEncontradoException("Grafo no encontrado: " + idGrafo));

        Recorrido recorrido = new Recorrido();
        recorrido.setNodoActual(grafo.getNodoRaiz());
        recorrido.setEstado(Recorrido.Estado.EN_CURSO);
        recorrido.setResultado(null);
        recorrido.setMensaje(null);
        return repoRecorrido.save(recorrido);
    }

    @Override
    public Recorrido responder(long recorridoId, String respuesta) {
        Recorrido recorrido = repoRecorrido.findById(recorridoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Recorrido no encontrado: " + recorridoId));

        Nodo nodoActual = recorrido.getNodoActual();
        if (nodoActual == null) {
            return recorrido;
        }

        String textoNodo = nodoActual.getTexto();
        List<Nodo> nodos = (List<Nodo>) repoNodo.findAll();

        if ("¿Tu bebé está respirando más rápido de lo usual?".equals(textoNodo)) {
            if ("no".equals(respuesta)) {
                recorrido.setNodoActual(null);
                recorrido.setEstado(Recorrido.Estado.FINALIZADO);
                recorrido.setResultado(Recorrido.Resultado.NORMAL);
                recorrido.setMensaje("No se detectan signos de alarma respiratoria en este momento.");
            } else if ("si".equals(respuesta)) {
                Nodo p2 = nodos.stream()
                    .filter(n -> "¿Cuál describe mejor a tu bebé ahora?".equals(n.getTexto()))
                    .findFirst()
                    .orElse(null);
                recorrido.setNodoActual(p2);
                recorrido.setEstado(Recorrido.Estado.EN_CURSO);
            }
        } else if ("¿Cuál describe mejor a tu bebé ahora?".equals(textoNodo)) {
            List<String> graves = List.of("cianosis", "apnea");
            recorrido.setNodoActual(null);
            recorrido.setEstado(Recorrido.Estado.FINALIZADO);
            if (graves.contains(respuesta)) {
                recorrido.setResultado(Recorrido.Resultado.REQUIERE_ATENCION);
                recorrido.setMensaje("Según lo que respondiste, es recomendable consultar a un profesional de salud para valorar a tu bebé.");
            } else {
                recorrido.setResultado(Recorrido.Resultado.NORMAL);
                recorrido.setMensaje("No se detectan signos de alarma respiratoria en este momento.");
            }
        }

        return repoRecorrido.save(recorrido);
    }

    @Override
    public Recorrido consultar(long recorridoId) {
        return repoRecorrido.findById(recorridoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Recorrido no encontrado: " + recorridoId));
    }
}
