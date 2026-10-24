package org.signbb.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.signbb.backend.seguridad.ApiPrueba.conToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.cache.RepositorioSesionRedis;
import org.signbb.backend.observaciones.datos.repositorios.RepositorioRecorridos;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.servicios.LimitadorIntentosLogin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * Reglas de acceso con la cadena de filtros real (requiere PostgreSQL y
 * Redis, como el CI): qué responde cada ruta sin token, con un token alterado
 * y con cada rol, y que un recorrido solo lo ve el padre que lo inició. Las
 * cuentas y recorridos creados se borran al terminar.
 */
@SpringBootTest
class ReglasAccesoIntegracionTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RepositorioUsuario usuarios;

    @Autowired
    private RepositorioRecorridos recorridos;

    @Autowired
    private StringRedisTemplate redis;

    private ApiPrueba api;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        api = new ApiPrueba(contexto, usuarios);
        mvc = api.mvc();
    }

    @AfterEach
    void limpiar() {
        api.limpiar();
    }

    /** Pide ingresar con ese correo y clave, sin exigir el resultado. */
    private org.springframework.test.web.servlet.ResultActions intentarIngreso(String correo, String clave)
            throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + clave + "\"}"));
    }

    @Test
    void sinTokenLaApiResponde401EnJson() throws Exception {
        mvc.perform(get("/api/autoria/subgrafos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.avisos").isArray());
        mvc.perform(post("/api/recorridos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    void lasRutasPublicasNoPidenToken() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/media/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(r -> assertNotEquals(401, r.getResponse().getStatus()));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"nadie-" + System.nanoTime() + "@prueba.signbb\",\"contrasena\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos."));
    }

    @Test
    void elPreflightDelFrontResponde200() throws Exception {
        mvc.perform(options("/api/autoria/subgrafos")
                .header("Origin", "http://localhost:4300")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4300"));
    }

    @Test
    void elSupervisorSembradoIngresaYSeVe() throws Exception {
        String token = api.ingresar(ApiPrueba.SUPERVISOR, ApiPrueba.CLAVE_SUPERVISOR);

        mvc.perform(conToken(get("/api/auth/yo"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("MEDICO_SUPERVISOR"))
                .andExpect(jsonPath("$.correo").value(ApiPrueba.SUPERVISOR));
    }

    @Test
    void cerrarTodasInvalidaLosTokensAnterioresYPermiteIngresarDeNuevo() throws Exception {
        ApiPrueba.Cuenta padre = api.registrarPadre("padre-cierre");
        String otroDispositivo = api.ingresar(padre.correo(), ApiPrueba.CLAVE);

        mvc.perform(conToken(post("/api/auth/cerrar-todas"), padre.token())).andExpect(status().isNoContent());

        mvc.perform(conToken(get("/api/auth/yo"), padre.token())).andExpect(status().isUnauthorized());
        mvc.perform(conToken(get("/api/auth/yo"), otroDispositivo)).andExpect(status().isUnauthorized());
        // El «iat» va en segundos: un token emitido en el segundo del cierre no sirve.
        Thread.sleep(1100);
        String nuevo = api.ingresar(padre.correo(), ApiPrueba.CLAVE);
        mvc.perform(conToken(get("/api/auth/yo"), nuevo)).andExpect(status().isOk());
    }

    @Test
    void cincoFallosBloqueanElIngresoAunConLaClaveCorrecta() throws Exception {
        ApiPrueba.Cuenta padre = api.registrarPadre("padre-bloqueo");
        try {
            for (int i = 0; i < 5; i++) {
                intentarIngreso(padre.correo(), "clave-mala").andExpect(status().isUnauthorized());
            }

            intentarIngreso(padre.correo(), "clave-mala").andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(r -> assertTrue(JsonPath.<String>read(r.getResponse().getContentAsString(), "$.mensaje")
                            .startsWith("Demasiados intentos")));
            intentarIngreso(padre.correo(), ApiPrueba.CLAVE).andExpect(status().isTooManyRequests());
            // Otro correo no se ve afectado.
            api.registrarPadre("padre-libre");
        } finally {
            redis.delete(LimitadorIntentosLogin.PREFIJO + padre.correo());
        }
    }

    @Test
    void unIngresoCorrectoReiniciaElContadorDeFallos() throws Exception {
        ApiPrueba.Cuenta padre = api.registrarPadre("padre-reinicio");
        try {
            for (int i = 0; i < 4; i++) {
                intentarIngreso(padre.correo(), "clave-mala").andExpect(status().isUnauthorized());
            }
            intentarIngreso(padre.correo(), ApiPrueba.CLAVE).andExpect(status().isOk());
            for (int i = 0; i < 4; i++) {
                intentarIngreso(padre.correo(), "clave-mala").andExpect(status().isUnauthorized());
            }
        } finally {
            redis.delete(LimitadorIntentosLogin.PREFIJO + padre.correo());
        }
    }

    @Test
    void cerrarTodasPideToken() throws Exception {
        mvc.perform(post("/api/auth/cerrar-todas")).andExpect(status().isUnauthorized());
    }

    @Test
    void unPadreNoEntraAAutoriaNiAUsuarios() throws Exception {
        String padre = api.registrarPadre("padre").token();

        mvc.perform(conToken(get("/api/autoria/subgrafos"), padre)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para hacer esto."));
        mvc.perform(conToken(get("/api/usuarios"), padre)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/auth/yo"), padre)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PADRE"));
    }

    @Test
    void unMedicoNoHaceRecorridosNiAdministraNiAprueba() throws Exception {
        String medico = api.crearClinico("medico", "MEDICO").token();

        mvc.perform(conToken(get("/api/autoria/categorias"), medico)).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/recorridos"), medico)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/usuarios"), medico)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/autoria/revisiones"), medico)).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/autoria/subgrafos/1/aprobar"), medico)).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/autoria/subgrafos/1/rechazar"), medico)).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/autoria/subgrafos/1/despublicar"), medico))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/autoria/subgrafos/1/publicar"), medico))
                .andExpect(status().isForbidden());
    }

    @Test
    void elRecorridoEsDeQuienLoInicio() throws Exception {
        String p1 = api.registrarPadre("p1").token();
        String p2 = api.registrarPadre("p2").token();

        String cuerpo = mvc.perform(conToken(post("/api/recorridos"), p1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(cuerpo, "$.recorridoId");
        try {
            mvc.perform(conToken(get("/api/recorridos/" + id), p1)).andExpect(status().isOk());
            mvc.perform(conToken(get("/api/recorridos"), p1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].recorridoId").value(id));

            mvc.perform(conToken(get("/api/recorridos/" + id), p2)).andExpect(status().isNotFound());
            mvc.perform(conToken(post("/api/recorridos/" + id + "/retroceder"), p2))
                    .andExpect(status().isNotFound());
            mvc.perform(conToken(post("/api/recorridos/" + id + "/respuestas"), p2)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"SI\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(conToken(get("/api/recorridos"), p2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        } finally {
            recorridos.deleteById(id);
        }
    }

    @Test
    void soloUnPadreGuardaElTelefonoDeSuPediatra() throws Exception {
        String padre = api.registrarPadre("pediatra").token();
        String medico = api.crearClinico("sin-pediatra", "MEDICO").token();

        mvc.perform(conToken(put("/api/auth/yo/pediatra"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\":\"+57 300 123 4567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoPediatra").value("+57 300 123 4567"));
        mvc.perform(conToken(get("/api/auth/yo"), padre))
                .andExpect(jsonPath("$.telefonoPediatra").value("+57 300 123 4567"));
        mvc.perform(conToken(put("/api/auth/yo/pediatra"), padre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\":\"llamar\"}"))
                .andExpect(status().is(422));
        mvc.perform(conToken(put("/api/auth/yo/pediatra"), medico).contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\":\"3001234567\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unRecorridoReiniciadoYaNoSeSigueYElNuevoSi() throws Exception {
        String padre = api.registrarPadre("reinicia").token();
        String viejo = JsonPath.read(mvc.perform(conToken(post("/api/recorridos"), padre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.recorridoId");
        String nuevo = null;
        try {
            nuevo = JsonPath.read(mvc.perform(conToken(post("/api/recorridos/" + viejo + "/reiniciar"), padre))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                    .andReturn().getResponse().getContentAsString(), "$.recorridoId");

            assertNotEquals(viejo, nuevo);
            mvc.perform(conToken(get("/api/recorridos/" + viejo), padre)).andExpect(status().isGone())
                    .andExpect(jsonPath("$.mensaje").exists());
            mvc.perform(conToken(get("/api/recorridos/" + nuevo), padre)).andExpect(status().isOk());
            mvc.perform(conToken(get("/api/recorridos"), padre))
                    .andExpect(jsonPath("$[?(@.recorridoId == '" + viejo + "')].estado").value("ABANDONADO"));
        } finally {
            recorridos.deleteById(viejo);
            if (nuevo != null) {
                recorridos.deleteById(nuevo);
            }
        }
    }

    @Test
    void unRecorridoSinSesionDa410YQuedaVencido() throws Exception {
        String padre = api.registrarPadre("vencido").token();
        String id = JsonPath.read(mvc.perform(conToken(post("/api/recorridos"), padre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.recorridoId");
        try {
            redis.delete(RepositorioSesionRedis.PREFIJO_SESION + id);

            mvc.perform(conToken(get("/api/recorridos/" + id), padre)).andExpect(status().isGone())
                    .andExpect(jsonPath("$.mensaje").value(
                            "Esta consulta venció tras 8 horas sin actividad. Inicia una nueva."));
            mvc.perform(conToken(get("/api/recorridos"), padre))
                    .andExpect(jsonPath("$[0].estado").value("VENCIDO"));
        } finally {
            recorridos.deleteById(id);
        }
    }

    @Test
    void unPadreNoPuedeCrearseComoMedico() throws Exception {
        String supervisor = api.ingresar(ApiPrueba.SUPERVISOR, ApiPrueba.CLAVE_SUPERVISOR);

        mvc.perform(conToken(post("/api/usuarios"), supervisor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"X\",\"correo\":\"" + api.correo("x")
                        + "\",\"contrasena\":\"clave-larga\",\"rol\":\"PADRE\"}"))
                .andExpect(status().is(422));
    }

    @Test
    void unTokenAlteradoDa401() throws Exception {
        String token = api.registrarPadre("alterado").token();
        int i = token.indexOf('.') + 5;
        String alterado = token.substring(0, i) + (token.charAt(i) == 'A' ? 'B' : 'A') + token.substring(i + 1);

        mvc.perform(conToken(get("/api/auth/yo"), alterado)).andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenDeUnaCuentaDesactivadaDejaDeServir() throws Exception {
        ApiPrueba.Cuenta medico = api.crearClinico("desactivado", "MEDICO");
        String supervisor = api.ingresar(ApiPrueba.SUPERVISOR, ApiPrueba.CLAVE_SUPERVISOR);

        mvc.perform(conToken(put("/api/usuarios/" + medico.id() + "/activo"), supervisor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mvc.perform(conToken(get("/api/auth/yo"), medico.token())).andExpect(status().isUnauthorized());
    }
}
