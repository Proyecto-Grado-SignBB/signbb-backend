package org.signbb.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.modelo.Rol;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Tests de {@link InicializadorSeguridad}: siembra el supervisor solo si no hay ninguno. */
class InicializadorSeguridadTest {

    private RepositorioUsuario usuarios;
    private PasswordEncoder codificador;
    private InicializadorSeguridad inicializador;

    @BeforeEach
    void preparar() {
        usuarios = mock(RepositorioUsuario.class);
        codificador = new BCryptPasswordEncoder(4);
        inicializador = new InicializadorSeguridad(usuarios, codificador, "Supervisor", " Sup@SignBB.local ",
                "Supervisor123");
    }

    @Test
    void sinSupervisorCreaUnoActivoConLaContrasenaCifrada() {
        when(usuarios.existsByRol(Rol.MEDICO_SUPERVISOR)).thenReturn(false);

        inicializador.run();

        ArgumentCaptor<UsuarioEntity> guardado = ArgumentCaptor.forClass(UsuarioEntity.class);
        verify(usuarios).save(guardado.capture());
        UsuarioEntity u = guardado.getValue();
        assertEquals(Rol.MEDICO_SUPERVISOR, u.getRol());
        assertEquals("sup@signbb.local", u.getCorreo());
        assertTrue(u.isActivo());
        assertTrue(codificador.matches("Supervisor123", u.getHashContrasena()));
    }

    @Test
    void siYaHaySupervisorNoHaceNada() {
        when(usuarios.existsByRol(Rol.MEDICO_SUPERVISOR)).thenReturn(true);

        inicializador.run();
        inicializador.run();

        verify(usuarios, never()).save(any());
    }
}
