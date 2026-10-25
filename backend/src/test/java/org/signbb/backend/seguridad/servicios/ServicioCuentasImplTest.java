package org.signbb.backend.seguridad.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.signbb.backend.seguridad.controladores.LoginRequest;
import org.signbb.backend.seguridad.controladores.NuevoUsuarioRequest;
import org.signbb.backend.seguridad.controladores.RegistroRequest;
import org.signbb.backend.seguridad.controladores.SesionDTO;
import org.signbb.backend.seguridad.controladores.UsuarioDTO;
import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Tests de {@link ServicioCuentasImpl}: registro de padres, ingreso con el
 * mismo mensaje para todo fallo y administración de cuentas clínicas.
 */
class ServicioCuentasImplTest {

    private RepositorioUsuario usuarios;
    private PasswordEncoder codificador;
    private ServicioTokens tokens;
    private ServicioCuentasImpl servicio;

    @BeforeEach
    void preparar() {
        usuarios = mock(RepositorioUsuario.class);
        codificador = new BCryptPasswordEncoder(4);
        tokens = mock(ServicioTokens.class);
        servicio = new ServicioCuentasImpl(usuarios, codificador, tokens);
        when(usuarios.save(any(UsuarioEntity.class))).thenAnswer(i -> {
            UsuarioEntity u = i.getArgument(0);
            if (u.getId() == null) {
                u.setId(7L);
            }
            return u;
        });
        when(tokens.emitir(any(UsuarioEntity.class), anyBoolean()))
                .thenReturn(new TokenEmitido("token", Instant.parse("2026-01-01T08:00:00Z")));
    }

    @Test
    void registrarPadreNormalizaElCorreoYGuardaSoloElHash() {
        SesionDTO sesion = servicio.registrarPadre(new RegistroRequest(" Ana ", "  Ana@Correo.COM ", "secreta123"));

        ArgumentCaptor<UsuarioEntity> guardado = ArgumentCaptor.forClass(UsuarioEntity.class);
        verify(usuarios).save(guardado.capture());
        UsuarioEntity u = guardado.getValue();
        assertEquals("ana@correo.com", u.getCorreo());
        assertEquals("Ana", u.getNombre());
        assertEquals(Rol.PADRE, u.getRol());
        assertNotEquals("secreta123", u.getHashContrasena());
        assertTrue(codificador.matches("secreta123", u.getHashContrasena()));
        assertEquals("token", sesion.token());
        assertEquals(Rol.PADRE, sesion.usuario().rol());
    }

    @Test
    void registrarConCorreoRepetidoDa422() {
        when(usuarios.existsByCorreo("ana@correo.com")).thenReturn(true);

        assertThrows(CuentaInvalidaException.class,
                () -> servicio.registrarPadre(new RegistroRequest("Ana", "ANA@correo.com", "secreta123")));
        verify(usuarios, never()).save(any());
    }

    @Test
    void registrarConContrasenaCortaOCorreoMaloDa422() {
        assertThrows(CuentaInvalidaException.class,
                () -> servicio.registrarPadre(new RegistroRequest("Ana", "ana@correo.com", "corta")));
        assertThrows(CuentaInvalidaException.class,
                () -> servicio.registrarPadre(new RegistroRequest("Ana", "sin-arroba", "secreta123")));
        assertThrows(CuentaInvalidaException.class,
                () -> servicio.registrarPadre(new RegistroRequest(" ", "ana@correo.com", "secreta123")));
    }

    @Test
    void unaContrasenaDeMasDe72BytesDa422PorqueBCryptSoloUsaLosPrimeros72() {
        // 37 eñes = 37 caracteres pero 74 bytes en UTF-8
        assertThrows(CuentaInvalidaException.class,
                () -> servicio.registrarPadre(new RegistroRequest("Ana", "ana@correo.com", "ñ".repeat(37))));
        verify(usuarios, never()).save(any(UsuarioEntity.class));
    }

    @Test
    void ingresarConDatosCorrectosDevuelveElToken() {
        cuenta(3L, "ana@correo.com", "secreta123", Rol.PADRE, true);

        SesionDTO sesion = servicio.ingresar(new LoginRequest(" ANA@correo.com", "secreta123"));

        assertEquals("token", sesion.token());
        assertEquals(3L, sesion.usuario().id());
    }

    @Test
    void ingresarFallaConElMismoMensajeSinImportarPorQue() {
        cuenta(3L, "ana@correo.com", "secreta123", Rol.PADRE, true);
        cuenta(4L, "inactiva@correo.com", "secreta123", Rol.MEDICO, false);

        String claveMala = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.ingresar(new LoginRequest("ana@correo.com", "otra-clave"))).getMessage();
        String noExiste = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.ingresar(new LoginRequest("nadie@correo.com", "secreta123"))).getMessage();
        String inactiva = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.ingresar(new LoginRequest("inactiva@correo.com", "secreta123"))).getMessage();
        String vacio = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.ingresar(new LoginRequest(null, null))).getMessage();

        assertEquals(ServicioCuentasImpl.MENSAJE_INGRESO, claveMala);
        assertEquals(claveMala, noExiste);
        assertEquals(claveMala, inactiva);
        assertEquals(claveMala, vacio);
    }

    @Test
    void unCorreoInexistenteTambienVerificaUnaContrasenaFicticia() {
        RepositorioUsuario usuariosVacios = mock(RepositorioUsuario.class);
        PasswordEncoder codificadorFalso = mock(PasswordEncoder.class);
        when(codificadorFalso.encode(anyString())).thenReturn("hash-ficticio");
        when(codificadorFalso.matches("secreta123", "hash-ficticio")).thenReturn(false);
        when(usuariosVacios.findByCorreo("nadie@correo.com")).thenReturn(Optional.empty());
        ServicioCuentasImpl servicioFalso = new ServicioCuentasImpl(usuariosVacios, codificadorFalso,
                mock(ServicioTokens.class));

        assertThrows(CredencialesInvalidasException.class,
                () -> servicioFalso.ingresar(new LoginRequest("nadie@correo.com", "secreta123")));

        verify(codificadorFalso).matches("secreta123", "hash-ficticio");
    }

    @Test
    void ingresarPasaElDispositivoSeguroAlEmisorDeTokens() {
        UsuarioEntity ana = cuenta(3L, "ana@correo.com", "secreta123", Rol.PADRE, true);

        servicio.ingresar(new LoginRequest("ana@correo.com", "secreta123", true));
        servicio.ingresar(new LoginRequest("ana@correo.com", "secreta123", false));
        servicio.ingresar(new LoginRequest("ana@correo.com", "secreta123"));

        verify(tokens).emitir(ana, true);
        verify(tokens, org.mockito.Mockito.times(2)).emitir(ana, false);
    }

    @Test
    void registrarPadrePasaElDispositivoSeguroAlEmisorDeTokens() {
        servicio.registrarPadre(new RegistroRequest("Ana", "ana@correo.com", "secreta123", true));
        servicio.registrarPadre(new RegistroRequest("Beto", "beto@correo.com", "secreta123"));

        verify(tokens).emitir(any(UsuarioEntity.class), eq(true));
        verify(tokens).emitir(any(UsuarioEntity.class), eq(false));
    }

    @Test
    void usuarioActivoIgnoraLasCuentasInactivas() {
        cuenta(3L, "ana@correo.com", "secreta123", Rol.PADRE, true);
        cuenta(4L, "inactiva@correo.com", "secreta123", Rol.MEDICO, false);

        assertEquals(Rol.PADRE, servicio.usuarioActivo(3L).orElseThrow().rol());
        assertTrue(servicio.usuarioActivo(4L).isEmpty());
        assertTrue(servicio.usuarioActivo(99L).isEmpty());
    }

    @Test
    void cerrarTodasLasSesionesInvalidaLosTokensEmitidosAntes() {
        UsuarioEntity ana = cuenta(3L, "ana@correo.com", "secreta123", Rol.PADRE, true);
        Instant antes = Instant.now().minusSeconds(60);

        servicio.cerrarTodasLasSesiones(ana.comoActual());

        assertTrue(ana.getSesionValidaDesde() != null);
        assertTrue(servicio.usuarioActivo(3L, antes).isEmpty());
        assertTrue(servicio.usuarioActivo(3L, null).isEmpty());
        assertEquals(Rol.PADRE, servicio.usuarioActivo(3L, Instant.now().plusSeconds(5)).orElseThrow().rol());
        verify(usuarios).save(ana);
    }

    @Test
    void sinCerrarSesionesTodoTokenValido() {
        cuenta(3L, "ana@correo.com", "secreta123", Rol.PADRE, true);

        assertEquals(Rol.PADRE, servicio.usuarioActivo(3L, Instant.now().minusSeconds(3600)).orElseThrow().rol());
        assertEquals(Rol.PADRE, servicio.usuarioActivo(3L, null).orElseThrow().rol());
    }

    @Test
    void usuarioActivoConFechaTambienIgnoraLasCuentasInactivas() {
        cuenta(4L, "inactiva@correo.com", "secreta123", Rol.MEDICO, false);

        assertTrue(servicio.usuarioActivo(4L, Instant.now()).isEmpty());
        assertTrue(servicio.usuarioActivo(99L, Instant.now()).isEmpty());
    }

    @Test
    void cerrarTodasLasSesionesDeUnaCuentaQueYaNoExisteDa401() {
        UsuarioActual fantasma = new UsuarioActual(99L, "Fantasma", "f@x.co", Rol.PADRE, null);

        assertThrows(CredencialesInvalidasException.class, () -> servicio.cerrarTodasLasSesiones(fantasma));
        verify(usuarios, never()).save(any(UsuarioEntity.class));
    }

    @Test
    void elSupervisorCreaUnMedicoConSuInstitucion() {
        UsuarioDTO creado = servicio.crearClinico(
                new NuevoUsuarioRequest("Dra. A", "a@clinica.co", "secreta123", Rol.MEDICO, " Fundación "));

        assertEquals(Rol.MEDICO, creado.rol());
        assertEquals("Fundación", creado.institucion());
        assertEquals("a@clinica.co", creado.correo());
    }

    @Test
    void crearUnPadreOSinRolPorLaApiClinicaDa422() {
        assertThrows(CuentaInvalidaException.class, () -> servicio.crearClinico(
                new NuevoUsuarioRequest("P", "p@correo.com", "secreta123", Rol.PADRE, null)));
        assertThrows(CuentaInvalidaException.class, () -> servicio.crearClinico(
                new NuevoUsuarioRequest("P", "p@correo.com", "secreta123", null, null)));
        verify(usuarios, never()).save(any());
    }

    @Test
    void listarClinicosPideSoloMedicosYSupervisores() {
        UsuarioEntity a = cuenta(5L, "a@clinica.co", "secreta123", Rol.MEDICO, true);
        when(usuarios.findByRolInOrderByNombreAsc(List.of(Rol.MEDICO, Rol.MEDICO_SUPERVISOR)))
                .thenReturn(List.of(a));

        assertEquals(List.of(5L), servicio.listarClinicos().stream().map(UsuarioDTO::id).toList());
    }

    @Test
    void elSupervisorNoPuedeDesactivarseASiMismo() {
        UsuarioActual supervisor = new UsuarioActual(1L, "Sup", "s@x.co", Rol.MEDICO_SUPERVISOR, null);

        assertThrows(CuentaInvalidaException.class, () -> servicio.cambiarActivo(1L, false, supervisor));
    }

    @Test
    void desactivarAOtraCuentaLaDejaInactiva() {
        UsuarioActual supervisor = new UsuarioActual(1L, "Sup", "s@x.co", Rol.MEDICO_SUPERVISOR, null);
        UsuarioEntity b = cuenta(6L, "b@clinica.co", "secreta123", Rol.MEDICO, true);

        UsuarioDTO dto = servicio.cambiarActivo(6L, false, supervisor);

        assertFalse(dto.activo());
        assertFalse(b.isActivo());
    }

    @Test
    void elPadreGuardaElTelefonoDeSuPediatraSinEspaciosSobrantes() {
        UsuarioEntity padre = cuenta(8L, "p@familia.co", "secreta123", Rol.PADRE, true);

        UsuarioDTO dto = servicio.guardarTelefonoPediatra(padre.comoActual(), "  +57 300 123 4567 ");

        assertEquals("+57 300 123 4567", dto.telefonoPediatra());
        assertEquals("+57 300 123 4567", padre.getTelefonoPediatra());
    }

    @Test
    void unTelefonoVacioBorraElNumeroGuardado() {
        UsuarioEntity padre = cuenta(8L, "p@familia.co", "secreta123", Rol.PADRE, true);
        padre.setTelefonoPediatra("3001234567");

        assertNull(servicio.guardarTelefonoPediatra(padre.comoActual(), "  ").telefonoPediatra());
        assertNull(padre.getTelefonoPediatra());
    }

    @Test
    void unTelefonoConLetrasOMuyCortoDa422YNoSeGuarda() {
        UsuarioEntity padre = cuenta(8L, "p@familia.co", "secreta123", Rol.PADRE, true);

        for (String malo : List.of("300-ABC-4567", "12345", "1".repeat(21))) {
            assertThrows(CuentaInvalidaException.class,
                    () -> servicio.guardarTelefonoPediatra(padre.comoActual(), malo));
        }
        verify(usuarios, never()).save(any());
    }

    @Test
    void elTelefonoDelPediatraVieneEnLaSesion() {
        UsuarioEntity padre = cuenta(8L, "p@familia.co", "secreta123", Rol.PADRE, true);
        padre.setTelefonoPediatra("3001234567");

        SesionDTO sesion = servicio.ingresar(new LoginRequest("p@familia.co", "secreta123"));

        assertEquals("3001234567", sesion.usuario().telefonoPediatra());
    }

    /** Registra en el repositorio simulado una cuenta con esos datos. */
    private UsuarioEntity cuenta(Long id, String correo, String clave, Rol rol, boolean activo) {
        UsuarioEntity u = new UsuarioEntity();
        u.setId(id);
        u.setCorreo(correo);
        u.setNombre("Usuario " + id);
        u.setHashContrasena(codificador.encode(clave));
        u.setRol(rol);
        u.setActivo(activo);
        when(usuarios.findByCorreo(correo)).thenReturn(Optional.of(u));
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return u;
    }
}
