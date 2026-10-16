package org.signbb.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Reglas de acceso con la cadena de filtros real (requiere PostgreSQL y
 * Redis, como el CI): qué responde cada ruta sin token, con un token alterado
 * y con cada rol. Las cuentas creadas se borran al terminar.
 */
@SpringBootTest
class ReglasAccesoIntegracionTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RepositorioUsuario usuarios;

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
                .content("{\"correo\":\"nadie@prueba.signbb\",\"contrasena\":\"x\"}"))
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
        mvc.perform(conToken(post("/api/autoria/subgrafos/1/despublicar"), medico))
                .andExpect(status().isForbidden());
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
