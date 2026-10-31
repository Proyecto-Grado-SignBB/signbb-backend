package org.signbb.backend.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.OpcionRequest;
import org.signbb.backend.autoria.controladores.ReglaRequest;
import org.signbb.backend.observaciones.controladores.RespuestaRequest;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.seguridad.controladores.LoginRequest;
import org.signbb.backend.seguridad.controladores.RegistroRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Tests de los límites de largo y de caracteres de los DTO de entrada, con el
 * validador de Jakarta (sin Spring): cada límite rechaza lo que se pasa y
 * deja pasar lo permitido.
 */
class ValidacionEntradasTest {

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static String mensajes(Object dto) {
        return VALIDADOR.validate(dto).stream().map(ConstraintViolation::getMessage).sorted()
                .collect(Collectors.joining(" | "));
    }

    private static String letras(int n) {
        return "a".repeat(n);
    }

    @Test
    void elNombreElCorreoYLaContrasenaTienenLimiteDeLargo() {
        assertTrue(mensajes(new RegistroRequest(letras(101), "ana@correo.com", "secreta123")).contains("100"));
        assertTrue(mensajes(new RegistroRequest("Ana", letras(255), "secreta123")).contains("254"));
        assertTrue(mensajes(new RegistroRequest("Ana", "ana@correo.com", letras(73))).contains("72"));
        assertTrue(mensajes(new LoginRequest(letras(255), "secreta123")).contains("254"));
        assertTrue(mensajes(new LoginRequest("ana@correo.com", letras(73))).contains("72"));
    }

    @Test
    void unaPreguntaTieneMaximoDiezRespuestasYCadaTextoDe200() {
        List<OpcionRequest> once = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            once.add(new OpcionRequest("r" + i, "Opción " + i, null));
        }
        assertTrue(mensajes(new NodoRequest(null, null, "¿Tiene fiebre?", null, null, once)).contains("10"));

        List<OpcionRequest> larga = List.of(new OpcionRequest("r1", letras(201), null));
        assertTrue(mensajes(new NodoRequest(null, null, "¿Tiene fiebre?", null, null, larga)).contains("200"));

        List<OpcionRequest> diez = new ArrayList<>(once.subList(0, 10));
        assertTrue(mensajes(new NodoRequest(null, null, "¿Tiene fiebre?", null, null, diez)).isEmpty());
    }

    @Test
    void unaReglaTieneMaximoVeinteCasosYVeinteCondicionesPorCaso() {
        List<List<Condicion>> veintiunCasos = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            veintiunCasos.add(List.of(new Condicion("n1", "SI")));
        }
        assertTrue(mensajes(new ReglaRequest("Regla", "desc", veintiunCasos, null, "destino")).contains("20 casos"));

        List<Condicion> veintiuna = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            veintiuna.add(new Condicion("n" + i, "SI"));
        }
        assertTrue(mensajes(new ReglaRequest("Regla", "desc", List.of(veintiuna), null, "destino"))
                .contains("20 condiciones"));

        assertTrue(mensajes(new ReglaRequest("Regla", "desc", List.of(veintiuna.subList(0, 20)), null, "destino"))
                .isEmpty());
    }

    @Test
    void laRespuestaDelCuidadorTieneLimiteDeLargoYDeCantidad() {
        assertTrue(mensajes(new RespuestaRequest("SI", null, null, null)).isEmpty());
        assertTrue(mensajes(new RespuestaRequest(letras(201), null, null, null)).contains("200"));
        assertTrue(mensajes(new RespuestaRequest(null, List.of(letras(201)), null, null)).contains("200"));

        List<String> veintiuna = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            veintiuna.add("opcion" + i);
        }
        assertTrue(mensajes(new RespuestaRequest(null, veintiuna, null, null)).contains("20 opciones"));
        assertTrue(mensajes(new RespuestaRequest("SI", null, null, letras(101))).contains("100"));
        assertTrue(mensajes(new RespuestaRequest("SI", null, null, "n1")).isEmpty());
    }
}
