package org.signbb.backend.autoria.servicios;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_A;
import static org.signbb.backend.seguridad.UsuariosPrueba.MEDICO_B;
import static org.signbb.backend.seguridad.UsuariosPrueba.SUPERVISOR;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.seguridad.servicios.AccesoDenegadoException;

/**
 * Tests de {@link GuardiaAutoria} con repositorios simulados: 404 si no
 * existe, 403 si no corresponde, y quién crea la base de reglas de un grafo.
 */
class GuardiaAutoriaTest {

    private RepositorioBorradorSubgrafo subgrafos;
    private RepositorioBorradorBaseRegla bases;
    private GuardiaAutoria guardia;
    private BorradorSubgrafoEntity subgrafo;

    @BeforeEach
    void preparar() {
        subgrafos = mock(RepositorioBorradorSubgrafo.class);
        bases = mock(RepositorioBorradorBaseRegla.class);
        subgrafo = subgrafoDeA();
        when(subgrafos.findById(1L)).thenReturn(Optional.of(subgrafo));
        when(subgrafos.findByGrafoId(anyString())).thenReturn(Optional.empty());
        when(subgrafos.findByGrafoId("grafo_fiebre")).thenReturn(Optional.of(subgrafo));
        BorradorBaseReglaEntity base = BaseReglasFiebre.borrador(DocumentoBaseRegla.vacio());
        base.setAutorId(MEDICO_A.id());
        when(bases.findById(base.getId())).thenReturn(Optional.of(base));

        BorradorBaseReglaEntity baseRaiz = new BorradorBaseReglaEntity();
        baseRaiz.setId(2L);
        baseRaiz.setGrafoId("grafo_raiz");
        baseRaiz.setAutorId(SUPERVISOR.id());
        when(bases.findById(2L)).thenReturn(Optional.of(baseRaiz));

        guardia = new GuardiaAutoria(subgrafos, bases);
    }

    private static BorradorSubgrafoEntity subgrafoDeA() {
        BorradorSubgrafoEntity b = LienzoFiebre.borrador(LienzoFiebre.completo());
        b.setAutorId(MEDICO_A.id());
        return b;
    }

    @Test
    void elAutorPasaYLosDemasNoEditanSuSubgrafo() {
        assertDoesNotThrow(() -> guardia.editarSubgrafo(1L, MEDICO_A));
        assertThrows(AccesoDenegadoException.class, () -> guardia.editarSubgrafo(1L, MEDICO_B));
        assertThrows(AccesoDenegadoException.class, () -> guardia.editarSubgrafo(1L, SUPERVISOR));
    }

    @Test
    void elSupervisorVeElSubgrafoDeOtroYOtroMedicoNo() {
        assertDoesNotThrow(() -> guardia.leerSubgrafo(1L, SUPERVISOR));
        assertThrows(AccesoDenegadoException.class, () -> guardia.leerSubgrafo(1L, MEDICO_B));
    }

    @Test
    void unSubgrafoQueNoExisteEs404() {
        assertThrows(RecursoNoEncontradoException.class, () -> guardia.leerSubgrafo(9L, MEDICO_A));
    }

    @Test
    void laBaseSigueLaMismaPolitica() {
        Long id = BaseReglasFiebre.borrador(DocumentoBaseRegla.vacio()).getId();
        assertDoesNotThrow(() -> guardia.editarBase(id, MEDICO_A));
        assertDoesNotThrow(() -> guardia.leerBase(id, SUPERVISOR));
        assertThrows(AccesoDenegadoException.class, () -> guardia.leerBase(id, MEDICO_B));
        assertThrows(AccesoDenegadoException.class, () -> guardia.editarBase(id, SUPERVISOR));
    }

    @Test
    void soloElAutorDelSubgrafoCreaSuBase() {
        assertDoesNotThrow(() -> guardia.crearBase("grafo_fiebre", MEDICO_A));
        assertThrows(AccesoDenegadoException.class, () -> guardia.crearBase("grafo_fiebre", MEDICO_B));
    }

    @Test
    void laBaseDelGrafoRaizNoSeCreaNiSeLeeNiSeEdita() {
        assertThrows(EdicionInvalidaException.class, () -> guardia.crearBase("grafo_raiz", SUPERVISOR));
        assertThrows(EdicionInvalidaException.class, () -> guardia.crearBase("grafo_raiz", MEDICO_A));
        assertThrows(RecursoNoEncontradoException.class, () -> guardia.leerBase(2L, SUPERVISOR));
        assertThrows(EdicionInvalidaException.class, () -> guardia.editarBase(2L, SUPERVISOR));
    }

    @Test
    void sinGrafoLaGuardiaDejaQueElServicioExpliqueElError() {
        assertDoesNotThrow(() -> guardia.crearBase(null, MEDICO_A));
        assertDoesNotThrow(() -> guardia.crearBase("  ", MEDICO_A));
    }

    @Test
    void enRevisionNadieEditaElSubgrafoNiSusReglas() {
        subgrafo.setEstado(EstadoBorrador.EN_REVISION);
        Long base = BaseReglasFiebre.borrador(DocumentoBaseRegla.vacio()).getId();

        EdicionInvalidaException e = assertThrows(EdicionInvalidaException.class,
                () -> guardia.editarSubgrafo(1L, MEDICO_A));
        assertEquals(GuardiaAutoria.EN_REVISION, e.getMessage());
        assertThrows(EdicionInvalidaException.class, () -> guardia.editarBase(base, MEDICO_A));
        assertThrows(EdicionInvalidaException.class, () -> guardia.crearBase("grafo_fiebre", MEDICO_A));
    }

    @Test
    void enRevisionElAutorPuedeRetirarloYElSupervisorVerlo() {
        subgrafo.setEstado(EstadoBorrador.EN_REVISION);

        assertDoesNotThrow(() -> guardia.autorSubgrafo(1L, MEDICO_A));
        assertDoesNotThrow(() -> guardia.leerSubgrafo(1L, SUPERVISOR));
        assertThrows(AccesoDenegadoException.class, () -> guardia.autorSubgrafo(1L, MEDICO_B));
    }

    @Test
    void elSupervisorAutorPublicaSusPropiosBorradores() {
        subgrafo.setAutorId(SUPERVISOR.id());

        assertDoesNotThrow(() -> guardia.publicarSubgrafo(1L, SUPERVISOR));
        assertThrows(AccesoDenegadoException.class, () -> guardia.publicarSubgrafo(1L, MEDICO_A));
    }

    @Test
    void elSupervisorNoPublicaDirectamenteLoDeOtroMedico() {
        assertThrows(AccesoDenegadoException.class, () -> guardia.publicarSubgrafo(1L, SUPERVISOR));
    }

    @Test
    void noSePublicaDirectamenteLoQueEstaEnRevision() {
        subgrafo.setAutorId(SUPERVISOR.id());
        subgrafo.setEstado(EstadoBorrador.EN_REVISION);

        assertThrows(EdicionInvalidaException.class, () -> guardia.publicarSubgrafo(1L, SUPERVISOR));
    }

    @Test
    void unPublicadoSinCambiosNoSeRepublicaPeroConReglasCambiadasSi() {
        subgrafo.setAutorId(SUPERVISOR.id());
        subgrafo.setEstado(EstadoBorrador.PUBLICADO);
        assertThrows(EdicionInvalidaException.class, () -> guardia.publicarSubgrafo(1L, SUPERVISOR));

        BorradorBaseReglaEntity base = BaseReglasFiebre.borrador(DocumentoBaseRegla.vacio());
        base.setEstado(EstadoBorrador.CAMBIOS_SIN_PUBLICAR);
        when(bases.findByGrafoId("grafo_fiebre")).thenReturn(Optional.of(base));

        assertDoesNotThrow(() -> guardia.publicarSubgrafo(1L, SUPERVISOR));
    }
}
