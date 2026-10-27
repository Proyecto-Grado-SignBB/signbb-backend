package org.signbb.backend.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.modelo.Rol;
import org.springframework.security.crypto.password.PasswordEncoder;

/** El seed no duplica cuentas ni subgrafos cuando ya existen. */
class SeedDemoServiceTest {

    private RepositorioUsuario usuarios;
    private RepositorioBorradorSubgrafo borradores;
    private RepositorioBorradorBaseRegla basesBorrador;
    private RepositorioGrafoClinico grafos;
    private ServicioAutoria autoria;
    private SeedDemoService servicio;

    @BeforeEach
    void preparar() {
        usuarios = mock(RepositorioUsuario.class);
        borradores = mock(RepositorioBorradorSubgrafo.class);
        basesBorrador = mock(RepositorioBorradorBaseRegla.class);
        grafos = mock(RepositorioGrafoClinico.class);
        autoria = mock(ServicioAutoria.class);

        SeedDemoProperties props = new SeedDemoProperties();
        props.getMedico().setNombre("Médico ejemplo - Neonatologo");
        props.getMedico().setCorreo("medico.ejemplo@signbb.local");
        props.getMedico().setContrasena("Medico123");
        props.getMedico().setInstitucion("Fundación Canguro");
        props.getPadre().setNombre("Padre ejemplo");
        props.getPadre().setCorreo("padre.ejemplo@signbb.local");
        props.getPadre().setContrasena("Padre123");

        servicio = new SeedDemoService(props, usuarios, mock(PasswordEncoder.class),
                borradores, basesBorrador, grafos, autoria);
    }

    private static UsuarioEntity usuario(Long id, String correo, Rol rol) {
        UsuarioEntity u = new UsuarioEntity();
        u.setId(id);
        u.setNombre("Cuenta " + id);
        u.setCorreo(correo);
        u.setRol(rol);
        u.setActivo(true);
        return u;
    }

    @Test
    void siTodoExisteNoCreaNiPublicaNada() {
        when(usuarios.findByCorreo("medico.ejemplo@signbb.local"))
                .thenReturn(Optional.of(usuario(1L, "medico.ejemplo@signbb.local", Rol.MEDICO)));
        when(usuarios.findByCorreo("padre.ejemplo@signbb.local"))
                .thenReturn(Optional.of(usuario(2L, "padre.ejemplo@signbb.local", Rol.PADRE)));
        when(borradores.existsByGrafoId(any())).thenReturn(true);

        servicio.sembrar();

        verify(borradores, never()).save(any());
        verify(basesBorrador, never()).save(any());
        verify(autoria, never()).publicar(any());
    }
}
