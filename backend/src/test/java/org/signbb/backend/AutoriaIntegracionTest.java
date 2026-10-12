package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.DespuesDeRequest;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.OpcionRequest;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de punta a punta de la autoría (requiere PostgreSQL y Redis, como el
 * CI): se arma un subgrafo de fiebre con el servicio, como lo haría el lienzo,
 * se publica y se recorre como cuidadora desde la pregunta inicial hasta su
 * conclusión. No es {@code @Transactional} porque publicar actúa después del
 * commit; al terminar se borran las filas creadas, se restaura el grafo raíz y
 * se invalida el contenido en cache y en memoria.
 */
@SpringBootTest
class AutoriaIntegracionTest {

    private static final String ETIQUETA = "Fiebre o temperatura alta (prueba)";

    @Autowired
    private ServicioAutoria autoria;

    @Autowired
    private ServicioNavegacion navegacion;

    @Autowired
    private RepositorioBorradorSubgrafo borradores;

    @Autowired
    private RepositorioGrafoClinico grafos;

    @Autowired
    private RepositorioVersionContenido versiones;

    @Autowired
    private ResolvedorContenidoClinico resolvedor;

    @Autowired
    private ContextoClinico contexto;

    private GrafoClinicoJson raizOriginal;
    private Long ultimaVersionAntes;
    private Long borradorId;
    private String grafoId;

    @BeforeEach
    void guardarRaiz() {
        raizOriginal = filaRaiz().getEstructura();
        ultimaVersionAntes = versiones.idMasReciente();
    }

    @AfterEach
    void restaurar() {
        if (borradorId != null) {
            borradores.deleteById(borradorId);
        }
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            GrafoClinicoJson e = fila.getEstructura();
            if (e != null && e.graph() != null && e.graph().id() != null && e.graph().id().equals(grafoId)) {
                grafos.delete(fila);
            }
        }
        // Las versiones que creó la prueba se borran: la vigente tiene que volver a ser la de antes
        versiones.findAll().stream()
                .filter(v -> ultimaVersionAntes == null || v.getId() > ultimaVersionAntes)
                .forEach(versiones::delete);
        GrafoClinicoEntity raiz = filaRaiz();
        raiz.setEstructura(raizOriginal);
        grafos.save(raiz);
        resolvedor.invalidar();
        contexto.recargar();
    }

    private GrafoClinicoEntity filaRaiz() {
        return grafos.findAll().stream()
                .filter(f -> f.getEstructura() != null && f.getEstructura().graph() != null
                        && CompositorGrafoClinico.ID_RAIZ.equals(f.getEstructura().graph().id()))
                .findFirst().orElseThrow();
    }

    @Test
    void unSubgrafoPublicadoDesdeElLienzoSeRecorreComoCuidadora() {
        SubgrafoDTO s = autoria.crear(new SubgrafoRequest("Fiebre prueba de integracion", "puente_neuro", ETIQUETA,
                "Dra. Prueba", "Hospital de prueba"));
        borradorId = s.id();
        grafoId = s.grafoId();

        s = autoria.agregarNodo(borradorId, new NodoRequest(null, TipoNodo.PREGUNTA,
                "¿Cuánto marca el termómetro?", false, null, List.of(
                        new OpcionRequest(null, "Menos de 38 °C", Gravedad.LEVE),
                        new OpcionRequest(null, "Más de 39 °C", Gravedad.GRAVE))));
        String inicio = s.inicio();
        NodoLienzo pregunta = s.nodos().get(0);
        String menos = pregunta.respuestas().get(0).id();
        String mas = pregunta.respuestas().get(1).id();
        autoria.agregarNodo(borradorId, new NodoRequest(new DespuesDeRequest(inicio, mas), TipoNodo.CONCLUSION,
                "Lleve al bebé a urgencias ahora.", null, Desenlace.URGENCIA, null));
        s = autoria.agregarNodo(borradorId, new NodoRequest(new DespuesDeRequest(inicio, menos),
                TipoNodo.CONCLUSION, "Dele líquidos y vuelva a medir en una hora.", null,
                Desenlace.RECOMENDACION, null));
        assertEquals(List.of(), s.avisos());

        s = autoria.publicar(borradorId);
        assertEquals(EstadoBorrador.PUBLICADO, s.estado());
        Long versionNueva = versiones.idMasReciente();
        assertNotNull(versionNueva, "publicar guarda una versión del contenido");
        assertTrue(ultimaVersionAntes == null || versionNueva > ultimaVersionAntes);

        RecorridoDTO r = navegacion.iniciar();
        String id = r.recorridoId();
        r = navegacion.responder(id, "neurologico");
        assertEquals("puente_neuro", r.pregunta().id());
        assertTrue(r.pregunta().opciones().contains("iniciar"), "la opción del sistema sigue");
        assertTrue(r.pregunta().opciones().contains(ETIQUETA), "y aparece la del subgrafo nuevo");

        r = navegacion.responder(id, ETIQUETA);
        assertEquals(grafoId + "/" + inicio, r.pregunta().id());
        assertEquals("SELECCION_UNICA", r.pregunta().tipoInteraccion());

        r = navegacion.responder(id, "Más de 39 °C");
        assertEquals("FINALIZADO", r.estado());
        assertEquals("URGENCIA", r.resultado().nivel());
        assertEquals("Lleve al bebé a urgencias ahora.", r.resultado().mensaje());
    }
}
