package org.signbb.backend.seguridad;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * Ayuda de las pruebas de integración de seguridad: un {@link MockMvc} con la
 * cadena de filtros real, ingreso y creación de cuentas por la API, y limpieza
 * de las cuentas creadas. Los correos llevan un sufijo único por ejecución.
 */
public class ApiPrueba {

    /** Supervisor sembrado por {@link InicializadorSeguridad} con los valores de desarrollo. */
    public static final String SUPERVISOR = "supervisor@signbb.local";
    public static final String CLAVE_SUPERVISOR = "Supervisor123";
    public static final String CLAVE = "clave-de-prueba";

    /**
     * Una cuenta creada en la prueba.
     *
     * @param id     id de la cuenta
     * @param correo correo con el que ingresa
     * @param token  token de su sesión
     */
    public record Cuenta(Long id, String correo, String token) {
    }

    private final MockMvc mvc;
    private final RepositorioUsuario usuarios;
    private final String sufijo = UUID.randomUUID().toString().substring(0, 8);
    private final List<String> creados = new ArrayList<>();

    public ApiPrueba(WebApplicationContext contexto, RepositorioUsuario usuarios) {
        this.mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        this.usuarios = usuarios;
    }

    public MockMvc mvc() {
        return mvc;
    }

    /** Un correo único para esta ejecución. */
    public String correo(String nombre) {
        String correo = nombre + "-" + sufijo + "@prueba.signbb";
        creados.add(correo);
        return correo;
    }

    /** Ingresa y devuelve el token. */
    public String ingresar(String correo, String clave) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + clave + "\"}"))
                .andReturn();
        if (r.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login " + correo + ": " + r.getResponse().getStatus());
        }
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    /** Registra un padre y abre su sesión. */
    public Cuenta registrarPadre(String nombre) throws Exception {
        String correo = correo(nombre);
        MvcResult r = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo
                        + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andReturn();
        if (r.getResponse().getStatus() != 201) {
            throw new IllegalStateException("registro " + nombre + ": " + r.getResponse().getStatus()
                    + " " + r.getResponse().getContentAsString());
        }
        return new Cuenta(idDe(correo), correo, JsonPath.read(r.getResponse().getContentAsString(), "$.token"));
    }

    /** El supervisor sembrado crea una cuenta clínica (MEDICO o MEDICO_SUPERVISOR) y abre su sesión. */
    public Cuenta crearClinico(String nombre, String rol) throws Exception {
        String correo = correo(nombre);
        MvcResult r = mvc.perform(conToken(post("/api/usuarios"), ingresar(SUPERVISOR, CLAVE_SUPERVISOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\",\"contrasena\":\""
                        + CLAVE + "\",\"rol\":\"" + rol + "\",\"institucion\":\"Fundación Prueba\"}"))
                .andReturn();
        if (r.getResponse().getStatus() != 201) {
            throw new IllegalStateException("crear " + nombre + ": " + r.getResponse().getStatus());
        }
        return new Cuenta(idDe(correo), correo, ingresar(correo, CLAVE));
    }

    /** Id de la cuenta con ese correo. */
    public Long idDe(String correo) {
        return usuarios.findByCorreo(correo).orElseThrow().getId();
    }

    /** Agrega el token a la petición. */
    public static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** Borra las cuentas creadas en esta ejecución. */
    public void limpiar() {
        creados.forEach(c -> usuarios.findByCorreo(c).ifPresent(usuarios::delete));
        creados.clear();
    }
}
