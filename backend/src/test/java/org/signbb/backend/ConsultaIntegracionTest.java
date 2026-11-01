package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.signbb.backend.seguridad.ApiPrueba.conToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.cache.ResolvedorVersiones;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioObservaciones;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioRecorridos;
import org.signbb.backend.seguridad.ApiPrueba;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * Flujo HTTP completo de la cuidadora (CU01/CU03/CU09) con PostgreSQL y Redis
 * reales: inicia un recorrido, responde por la API, llega a un resultado y lo
 * vuelve a consultar.
 *
 * <p>
 * La fuente de datos es el fixture de navegación de
 * {@code src/test/resources/static/grafo_sistema/}, cargado con
 * {@link ContenidoEjemplo}. La prueba registra una versión de contenido propia
 * y la borra al terminar; no depende del seed de demo ni de la versión vigente
 * del entorno.
 */
@SpringBootTest
class ConsultaIntegracionTest {

    private static final String RESPUESTA_INICIAR = "iniciar";
    private static final String NINGUNO = "No, ninguno de los anteriores";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RepositorioUsuario usuarios;

    @Autowired
    private RepositorioRecorridos recorridos;

    @Autowired
    private RepositorioObservaciones observaciones;

    @Autowired
    private RepositorioSesion sesiones;

    @Autowired
    private ResolvedorVersiones versiones;

    @Autowired
    private RepositorioVersionContenido repositorioVersiones;

    @Autowired
    private ContextoClinico contextoClinico;

    private MockMvc mvc;
    private ApiPrueba api;
    private ApiPrueba.Cuenta padre;
    private Long versionPrueba;
    private final List<String> recorridosCreados = new ArrayList<>();

    @BeforeEach
    void preparar() throws Exception {
        api = new ApiPrueba(contexto, usuarios);
        mvc = api.mvc();

        CompositorGrafoClinico.Contenido contenido = ContenidoEjemplo.compuesto();
        versionPrueba = versiones.registrar(contenido.grafo(), contenido.reglas(),
                "Integración del flujo de la cuidadora");
        contextoClinico.recargar();

        padre = api.registrarPadre("cuidadora-integracion");
    }

    @AfterEach
    void limpiar() {
        for (String id : recorridosCreados) {
            sesiones.eliminar(id);
            observaciones.deleteAll(observaciones.findByRecorridoIdOrderByTimestampAsc(id));
            recorridos.deleteById(id);
        }
        recorridosCreados.clear();

        if (versionPrueba != null) {
            versiones.olvidar(List.of(versionPrueba));
            repositorioVersiones.deleteById(versionPrueba);
            contextoClinico.recargar();
        }
        if (api != null) {
            api.limpiar();
        }
    }

    @Test
    void elRecorridoNormalLlegaANormalYSePuedeConsultarDespues() throws Exception {
        String id = iniciar();

        assertEquals("puente_respiracion", responder(id, "{\"respuesta\":\"respiracion\"}"));
        assertEquals("grafo_respiracion/q_resp_urgencia",
                responder(id, "{\"respuesta\":\"" + RESPUESTA_INICIAR + "\"}"));
        assertEquals("grafo_respiracion/q_resp_consulta",
                responder(id, "{\"respuesta\":\"" + NINGUNO + "\"}"));
        assertEquals("grafo_respiracion/q_resp_normal",
                responder(id, "{\"respuesta\":\"" + NINGUNO + "\"}"));

        String cuerpo = responderCuerpo(id, "{\"respuesta\":\"No, no presenta anomalías\"}");
        assertEquals("FINALIZADO", JsonPath.read(cuerpo, "$.estado"));
        assertEquals("NORMAL", JsonPath.read(cuerpo, "$.resultado.nivel"));
        assertEquals("grafo_respiracion/normal_1", JsonPath.read(cuerpo, "$.resultado.destinoId"));
        assertTrue(((List<?>) JsonPath.read(cuerpo, "$.resultado.razones")).isEmpty());

        MvcResult consulta = mvc.perform(conToken(get("/api/recorridos/{id}", id), padre.token()))
                .andExpect(status().isOk()).andReturn();
        String consultado = consulta.getResponse().getContentAsString();
        assertEquals("FINALIZADO", JsonPath.read(consultado, "$.estado"));
        assertEquals("NORMAL", JsonPath.read(consultado, "$.resultado.nivel"));
    }

    @Test
    void unSignoDeAlarmaLlegaAUrgenciaPorLaApi() throws Exception {
        String id = iniciar();

        responder(id, "{\"respuesta\":\"respiracion\"}");
        responder(id, "{\"respuesta\":\"" + RESPUESTA_INICIAR + "\"}");

        String cuerpo = responderCuerpo(id, "{\"respuesta\":\"Quejido al respirar\"}");
        assertEquals("FINALIZADO", JsonPath.read(cuerpo, "$.estado"));
        assertEquals("URGENCIA", JsonPath.read(cuerpo, "$.resultado.nivel"));
        assertEquals("grafo_respiracion/urgencia_1", JsonPath.read(cuerpo, "$.resultado.destinoId"));
        assertTrue(((List<?>) JsonPath.read(cuerpo, "$.resultado.razones"))
                .contains("Regla Respiración Urgencia"));
        assertTrue(((List<?>) JsonPath.read(cuerpo, "$.resultado.caminoCausal")).size() > 0);
    }

    /** Inicia un recorrido con el padre de la prueba y devuelve su id. */
    private String iniciar() throws Exception {
        MvcResult r = mvc.perform(conToken(post("/api/recorridos"), padre.token()))
                .andExpect(status().isOk()).andReturn();
        String cuerpo = r.getResponse().getContentAsString();
        String id = JsonPath.read(cuerpo, "$.recorridoId");
        recorridosCreados.add(id);
        assertEquals("pregunta_inicial", JsonPath.read(cuerpo, "$.pregunta.id"));
        List<String> opciones = JsonPath.read(cuerpo, "$.pregunta.opciones");
        assertTrue(opciones.contains("respiracion"), "opciones: " + opciones);
        return id;
    }

    /** Responde y devuelve el id de la pregunta siguiente. */
    private String responder(String recorridoId, String json) throws Exception {
        return JsonPath.read(responderCuerpo(recorridoId, json), "$.pregunta.id");
    }

    /** Responde y devuelve el cuerpo JSON completo. */
    private String responderCuerpo(String recorridoId, String json) throws Exception {
        MvcResult r = mvc.perform(conToken(post("/api/recorridos/{id}/respuestas", recorridoId), padre.token())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
        String cuerpo = r.getResponse().getContentAsString();
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("respuesta " + json + " dio " + r.getResponse().getStatus() + ": " + cuerpo);
        }
        return cuerpo;
    }
}
