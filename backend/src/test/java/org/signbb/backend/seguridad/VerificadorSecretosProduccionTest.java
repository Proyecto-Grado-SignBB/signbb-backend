package org.signbb.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Tests de {@link VerificadorSecretosProduccion}: en producción no se aceptan los valores de ejemplo. */
class VerificadorSecretosProduccionTest {

    private static final String JWT_PROPIO = "un-secreto-propio-de-mas-de-treinta-y-dos-bytes";
    private static final String CLAVE_PROPIA = "clave-propia-1";

    private static VerificadorSecretosProduccion verificador(String entorno, String jwt, String supervisor,
            String acceso, String secreto, boolean docsActivas) {
        return new VerificadorSecretosProduccion(entorno, jwt, supervisor, acceso, secreto, docsActivas);
    }

    @Test
    void enDesarrolloAceptaLosValoresDeEjemplo() {
        assertDoesNotThrow(() -> verificador("desarrollo", VerificadorSecretosProduccion.JWT_DESARROLLO,
                VerificadorSecretosProduccion.CONTRASENA_SUPERVISOR_DESARROLLO,
                VerificadorSecretosProduccion.MEDIA_ACCESO_DESARROLLO,
                VerificadorSecretosProduccion.MEDIA_SECRETO_DESARROLLO, true));
    }

    @Test
    void enProduccionAceptaSecretosPropiosConLaDocumentacionApagada() {
        assertDoesNotThrow(
                () -> verificador("produccion", JWT_PROPIO, CLAVE_PROPIA, CLAVE_PROPIA, CLAVE_PROPIA, false));
        assertDoesNotThrow(
                () -> verificador(" Producción ", JWT_PROPIO, CLAVE_PROPIA, CLAVE_PROPIA, CLAVE_PROPIA, false));
    }

    @Test
    void enProduccionRechazaLaDocumentacionActivaAunqueLosSecretosSeanPropios() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> verificador("produccion", JWT_PROPIO, CLAVE_PROPIA, CLAVE_PROPIA, CLAVE_PROPIA, true));

        assertTrue(ex.getMessage().contains("SIGNBB_DOCS_ACTIVAS"));
        assertFalse(ex.getMessage().contains("SIGNBB_JWT_SECRETO"));
    }

    @Test
    void enProduccionRechazaElSecretoJwtDeEjemploSinMostrarSuValor() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> verificador("produccion", VerificadorSecretosProduccion.JWT_DESARROLLO, CLAVE_PROPIA,
                        CLAVE_PROPIA, CLAVE_PROPIA, false));

        assertTrue(ex.getMessage().contains("SIGNBB_JWT_SECRETO"));
        assertFalse(ex.getMessage().contains(VerificadorSecretosProduccion.JWT_DESARROLLO));
        assertFalse(ex.getMessage().contains("SIGNBB_SUPERVISOR_CONTRASENA"));
    }

    @Test
    void enProduccionNombraTodasLasVariablesConValorDeEjemplo() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> verificador("produccion", VerificadorSecretosProduccion.JWT_DESARROLLO,
                        VerificadorSecretosProduccion.CONTRASENA_SUPERVISOR_DESARROLLO,
                        VerificadorSecretosProduccion.MEDIA_ACCESO_DESARROLLO,
                        VerificadorSecretosProduccion.MEDIA_SECRETO_DESARROLLO, true));

        String mensaje = ex.getMessage();
        assertTrue(mensaje.contains("SIGNBB_JWT_SECRETO"));
        assertTrue(mensaje.contains("SIGNBB_SUPERVISOR_CONTRASENA"));
        assertTrue(mensaje.contains("SIGNBB_MEDIA_ACCESS_KEY"));
        assertTrue(mensaje.contains("SIGNBB_MEDIA_SECRET_KEY"));
        assertTrue(mensaje.contains("SIGNBB_DOCS_ACTIVAS"));
        assertFalse(mensaje.contains(VerificadorSecretosProduccion.CONTRASENA_SUPERVISOR_DESARROLLO));
        assertFalse(mensaje.contains(VerificadorSecretosProduccion.MEDIA_SECRETO_DESARROLLO));
    }

    @Test
    void unEntornoDesconocidoImpideArrancar() {
        assertThrows(IllegalStateException.class,
                () -> verificador("produccon", JWT_PROPIO, CLAVE_PROPIA, CLAVE_PROPIA, CLAVE_PROPIA, false));
        assertThrows(IllegalStateException.class,
                () -> verificador("", JWT_PROPIO, CLAVE_PROPIA, CLAVE_PROPIA, CLAVE_PROPIA, false));
        assertThrows(IllegalStateException.class,
                () -> verificador(null, JWT_PROPIO, CLAVE_PROPIA, CLAVE_PROPIA, CLAVE_PROPIA, false));
    }

    @Test
    void losValoresDeEjemploDelVerificadorSiguenIgualesALosDeLaConfiguracion() throws IOException {
        String principal = leer("/application.properties");
        String docker = leer("/application-docker.properties");

        assertTrue(principal.contains(VerificadorSecretosProduccion.JWT_DESARROLLO));
        assertTrue(principal.contains(VerificadorSecretosProduccion.CONTRASENA_SUPERVISOR_DESARROLLO));
        assertTrue(docker.contains(VerificadorSecretosProduccion.MEDIA_ACCESO_DESARROLLO));
        assertTrue(docker.contains(VerificadorSecretosProduccion.MEDIA_SECRETO_DESARROLLO));
    }

    @Test
    void laVariableDeDocumentacionApagaSwaggerYElContrato() throws IOException {
        String principal = leer("/application.properties");

        assertTrue(principal.contains("signbb.docs.activas=${SIGNBB_DOCS_ACTIVAS:true}"));
        assertTrue(principal.contains("springdoc.api-docs.enabled=${signbb.docs.activas}"));
        assertTrue(principal.contains("springdoc.swagger-ui.enabled=${signbb.docs.activas}"));
    }

    private static String leer(String recurso) throws IOException {
        try (InputStream in = VerificadorSecretosProduccionTest.class.getResourceAsStream(recurso)) {
            assertTrue(in != null, "No se encontró " + recurso);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
