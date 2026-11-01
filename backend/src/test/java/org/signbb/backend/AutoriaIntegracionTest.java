package org.signbb.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.autoria.controladores.BaseReglaDTO;
import org.signbb.backend.autoria.controladores.BaseReglaRequest;
import org.signbb.backend.autoria.controladores.DespuesDeRequest;
import org.signbb.backend.autoria.controladores.NodoRequest;
import org.signbb.backend.autoria.controladores.OpcionRequest;
import org.signbb.backend.autoria.controladores.ReglaRequest;
import org.signbb.backend.autoria.controladores.SubgrafoDTO;
import org.signbb.backend.autoria.controladores.SubgrafoRequest;
import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.Desenlace;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.modelo.Gravedad;
import org.signbb.backend.autoria.modelo.NodoLienzo;
import org.signbb.backend.autoria.modelo.TipoNodo;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
import org.signbb.backend.autoria.servicios.ServicioAutoriaReglas;
import org.signbb.backend.cache.ResolvedorContenidoClinico;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.ContenidoEjemplo;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.servicios.ServicioNavegacion;
import org.signbb.backend.observaciones.controladores.RecorridoDTO;
import org.signbb.backend.reglas.datos.entidades.BaseReglasEntity;
import org.signbb.backend.reglas.datos.repositorios.RepositorioBaseReglas;
import org.signbb.backend.reglas.modelo.Condicion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de punta a punta de la autoría (requiere PostgreSQL y Redis, como el
 * CI): se arma un subgrafo de fiebre con el servicio, como lo haría el lienzo,
 * se publica y se recorre como cuidadora desde la pregunta inicial hasta su
 * conclusión. No es {@code @Transactional} porque publicar actúa después del
 * commit; al terminar se borran las filas creadas, se restaura el grafo raíz y
 * se invalida el contenido en cache y en memoria.
 *
 * <p>
 * El autor es una cuenta real de médico (se crea la primera vez): la base
 * exige que {@code autor_id} exista en {@code usuario}.
 */
@SpringBootTest
class AutoriaIntegracionTest {

    private static final String ETIQUETA = "Fiebre o temperatura alta (prueba)";

    private static final String CORREO_AUTOR = "autor-integracion@signbb.local";

    @Autowired
    private ServicioAutoria autoria;

    @Autowired
    private ServicioNavegacion navegacion;

    @Autowired
    private RepositorioBorradorSubgrafo borradores;

    @Autowired
    private RepositorioGrafoClinico grafos;

    @Autowired
    private RepositorioVersionContenido versiones;

    @Autowired
    private ResolvedorContenidoClinico resolvedor;

    @Autowired
    private ContextoClinico contexto;

    @Autowired
    private ServicioAutoriaReglas servicioReglas;

    @Autowired
    private RepositorioBorradorBaseRegla basesBorrador;

    @Autowired
    private RepositorioBaseReglas basesPublicadas;

    @Autowired
    private RepositorioUsuario usuarios;

    private UsuarioActual autor;

    private GrafoClinicoJson raizOriginal;
    private Long ultimaVersionAntes;
    private Long borradorId;
    private String grafoId;

    @BeforeEach
    void guardarRaiz() {
        GrafoClinicoEntity raiz = filaRaiz();
        raizOriginal = raiz.getEstructura();
        raiz.setEstructura(ContenidoEjemplo.raiz());
        grafos.save(raiz);
        resolvedor.invalidar();
        contexto.recargar();
        ultimaVersionAntes = versiones.idMasReciente();
        UsuarioEntity cuenta = usuarios.findByCorreo(CORREO_AUTOR).orElseGet(() -> {
            UsuarioEntity u = new UsuarioEntity();
            u.setCorreo(CORREO_AUTOR);
            u.setNombre("Dra. Prueba");
            u.setHashContrasena("sin-ingreso");
            u.setRol(Rol.MEDICO);
            u.setInstitucion("Hospital de prueba");
            u.setActivo(true);
            u.setCreadoEn(Instant.now());
            return usuarios.save(u);
        });
        autor = new UsuarioActual(cuenta.getId(), cuenta.getNombre(), cuenta.getCorreo(), cuenta.getRol(),
                cuenta.getInstitucion());
    }

    @AfterEach
    void restaurar() {
        if (grafoId != null) {
            basesBorrador.findByGrafoId(grafoId).ifPresent(basesBorrador::delete);
            for (BaseReglasEntity fila : basesPublicadas.findAll()) {
                if (fila.getEstructura() != null && grafoId.equals(fila.getEstructura().grafoId())) {
                    basesPublicadas.delete(fila);
                }
            }
        }
        if (borradorId != null) {
            borradores.deleteById(borradorId);
        }
        for (GrafoClinicoEntity fila : grafos.findAll()) {
            GrafoClinicoJson e = fila.getEstructura();
            if (e != null && e.graph() != null && e.graph().id() != null && e.graph().id().equals(grafoId)) {
                grafos.delete(fila);
            }
        }
        // Las versiones que creó la prueba se borran: la vigente tiene que volver a ser la de antes
        versiones.findAll().stream()
                .filter(v -> ultimaVersionAntes == null || v.getId() > ultimaVersionAntes)
                .forEach(versiones::delete);
        GrafoClinicoEntity raiz = filaRaiz();
        raiz.setEstructura(raizOriginal);
        grafos.save(raiz);
        resolvedor.invalidar();
        contexto.recargar();
    }

    private GrafoClinicoEntity filaRaiz() {
        return grafos.findAll().stream()
                .filter(f -> f.getEstructura() != null && f.getEstructura().graph() != null
                        && CompositorGrafoClinico.ID_RAIZ.equals(f.getEstructura().graph().id()))
                .findFirst().orElseThrow();
    }

    @Test
    void unSubgrafoPublicadoDesdeElLienzoSeRecorreComoCuidadora() {
        SubgrafoDTO s = autoria.crear(new SubgrafoRequest("Fiebre prueba de integracion", "puente_neuro", ETIQUETA,
                "Dra. Prueba", "Hospital de prueba"), autor);
        borradorId = s.id();
        grafoId = s.grafoId();

        s = autoria.agregarNodo(borradorId, new NodoRequest(null, TipoNodo.PREGUNTA,
                "¿Cuánto marca el termómetro?", false, null, List.of(
                        new OpcionRequest(null, "Menos de 38 °C", Gravedad.LEVE),
                        new OpcionRequest(null, "Más de 39 °C", Gravedad.GRAVE))));
        String inicio = s.inicio();
        NodoLienzo pregunta = s.nodos().get(0);
        String menos = pregunta.respuestas().get(0).id();
        String mas = pregunta.respuestas().get(1).id();
        autoria.agregarNodo(borradorId, new NodoRequest(new DespuesDeRequest(inicio, mas), TipoNodo.CONCLUSION,
                "Lleve al bebé a urgencias ahora.", null, Desenlace.URGENCIA, null));
        s = autoria.agregarNodo(borradorId, new NodoRequest(new DespuesDeRequest(inicio, menos),
                TipoNodo.CONCLUSION, "Dele líquidos y vuelva a medir en una hora.", null,
                Desenlace.RECOMENDACION, null));
        assertEquals(List.of(), s.avisos());

        long versionesAntes = versiones.count();
        s = autoria.publicar(borradorId);
        assertEquals(EstadoBorrador.PUBLICADO, s.estado());
        assertEquals(versionesAntes + 1, versiones.count(), "publicar registra una sola versión");
        Long versionNueva = versiones.idMasReciente();
        assertNotNull(versionNueva, "publicar guarda una versión del contenido");
        assertTrue(ultimaVersionAntes == null || versionNueva > ultimaVersionAntes);

        RecorridoDTO r = navegacion.iniciar();
        String id = r.recorridoId();
        r = navegacion.responder(id, "Actividad y color");
        assertEquals("puente_neuro", r.pregunta().id());
        assertTrue(r.pregunta().opciones().contains(ETIQUETA), "aparece la opción del subgrafo nuevo");

        r = navegacion.responder(id, ETIQUETA);
        assertEquals(grafoId + "/" + inicio, r.pregunta().id());
        assertEquals("SELECCION_UNICA", r.pregunta().tipoInteraccion());

        r = navegacion.responder(id, "Más de 39 °C");
        assertEquals("FINALIZADO", r.estado());
        assertEquals("URGENCIA", r.resultado().nivel());
        assertEquals("Lleve al bebé a urgencias ahora.", r.resultado().mensaje());
    }

    @Test
    void publicarElSubgrafoPublicaTambienSuBaseDeReglas() {
        String etiqueta = "Fiebre con reglas (prueba)";
        SubgrafoDTO s = autoria.crear(new SubgrafoRequest("Fiebre con reglas", "puente_neuro", etiqueta,
                "Dra. Prueba", "Hospital de prueba"), autor);
        borradorId = s.id();
        grafoId = s.grafoId();

        s = autoria.agregarNodo(borradorId, new NodoRequest(null, TipoNodo.PREGUNTA,
                "¿Cuánto marca el termómetro?", false, null, List.of(
                        new OpcionRequest(null, "Menos de 38 °C", Gravedad.LEVE),
                        new OpcionRequest(null, "Más de 39 °C", Gravedad.GRAVE))));
        String inicio = s.inicio();
        NodoLienzo pregunta = s.nodos().get(0);
        String menos = pregunta.respuestas().get(0).id();
        String mas = pregunta.respuestas().get(1).id();
        s = autoria.agregarNodo(borradorId, new NodoRequest(new DespuesDeRequest(inicio, mas),
                TipoNodo.CONCLUSION, "Lleve al bebé a urgencias ahora.", null, Desenlace.URGENCIA, null));
        String urgId = s.nodos().stream()
                .filter(n -> "Lleve al bebé a urgencias ahora.".equals(n.texto()))
                .findFirst().orElseThrow().id();
        autoria.agregarNodo(borradorId, new NodoRequest(new DespuesDeRequest(inicio, menos),
                TipoNodo.CONCLUSION, "Dele líquidos y vuelva a medir en una hora.", null,
                Desenlace.RECOMENDACION, null));

        // Base de reglas del subgrafo: «Menos de 38 °C» también debe llevar a urgencias.
        BaseReglaDTO base = servicioReglas.crear(new BaseReglaRequest("Reglas de fiebre", null, grafoId,
                "Dra. Prueba", "Hospital de prueba"), autor);
        servicioReglas.agregarRegla(base.id(), new ReglaRequest("R fiebre alta", null,
                List.of(List.of(new Condicion(inicio, "Menos de 38 °C"))), NivelAlerta.URGENCIA, urgId));

        // Publicar el subgrafo publica también la base (acción única).
        long versionesAntes = versiones.count();
        s = autoria.publicar(borradorId);
        assertEquals(EstadoBorrador.PUBLICADO, s.estado());
        assertEquals(versionesAntes + 1, versiones.count(), "publicar registra una sola versión");
        var versionPublicada = versiones.findById(versiones.idMasReciente()).orElseThrow();
        assertNotNull(versionPublicada.getReglas());
        assertFalse(versionPublicada.getReglas().rules().isEmpty());
        assertTrue(basesPublicadas.findAll().stream().anyMatch(f -> f.getEstructura() != null
                        && grafoId.equals(f.getEstructura().grafoId())),
                "la base de reglas queda publicada junto con el subgrafo");

        RecorridoDTO r = navegacion.iniciar();
        String id = r.recorridoId();
        navegacion.responder(id, "Actividad y color");
        navegacion.responder(id, etiqueta);
        r = navegacion.responder(id, "Menos de 38 °C");
        assertEquals("FINALIZADO", r.estado());
        assertEquals("URGENCIA", r.resultado().nivel(), "la regla desvía a urgencias");
    }
}
