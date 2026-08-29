package org.signbb.backend.config;

import org.signbb.backend.navegacion.repositorios.RepositorioGrafo;
import org.signbb.backend.navegacion.repositorios.RepositorioNodo;
import org.signbb.backend.navegacion.servicios.Grafo;
import org.signbb.backend.navegacion.servicios.Nodo;
import org.signbb.backend.navegacion.servicios.OpcionRespuesta;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseInit implements CommandLineRunner {

    private final RepositorioNodo repositorioNodo;
    private final RepositorioGrafo repositorioGrafo;

    public DatabaseInit(RepositorioNodo repositorioNodo, RepositorioGrafo repositorioGrafo) {
        this.repositorioNodo = repositorioNodo;
        this.repositorioGrafo = repositorioGrafo;
    }

    @Override
    public void run(String... args) {
        OpcionRespuesta si = new OpcionRespuesta("Sí", "si");
        OpcionRespuesta no = new OpcionRespuesta("No", "no");

        Nodo p1 = new Nodo();
        p1.setTexto("¿Tu bebé está respirando más rápido de lo usual?");
        p1.setOpcionesRespuesta(List.of(si, no));
        p1 = repositorioNodo.save(p1);

        Nodo p2 = new Nodo();
        p2.setTexto("¿Cuál describe mejor a tu bebé ahora?");
        p2.setOpcionesRespuesta(List.of(
            new OpcionRespuesta("Respiración más agitada de lo usual", "agitada"),
            new OpcionRespuesta("Piel o labios con tono azulado", "cianosis"),
            new OpcionRespuesta("Quejido leve al respirar", "quejido"),
            new OpcionRespuesta("Pausas cortas al respirar (apnea)", "apnea"),
            new OpcionRespuesta("Ninguna de las anteriores", "ninguna")
        ));
        p2 = repositorioNodo.save(p2);

        Grafo grafoDificultadRespiratoria = new Grafo();
        grafoDificultadRespiratoria.setNombre("Dificultad respiratoria");
        grafoDificultadRespiratoria.setNodoRaiz(p1);
        repositorioGrafo.save(grafoDificultadRespiratoria);
    }
}
