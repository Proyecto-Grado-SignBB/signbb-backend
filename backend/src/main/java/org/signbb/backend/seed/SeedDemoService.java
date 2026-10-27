package org.signbb.backend.seed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.signbb.backend.autoria.datos.entidades.BorradorBaseReglaEntity;
import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorBaseRegla;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;
import org.signbb.backend.autoria.servicios.ServicioAutoria;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoParser;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasParser;
import org.signbb.backend.seguridad.datos.entidades.UsuarioEntity;
import org.signbb.backend.seguridad.datos.repositorios.RepositorioUsuario;
import org.signbb.backend.seguridad.modelo.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Seed de demo: cuentas de ejemplo y publicación de los cuatro subgrafos del
 * manifiesto {@code seed/demo/seed.json}.
 *
 * <p>
 * No crea el supervisor: lo hace {@code InicializadorSeguridad}. Tampoco crea
 * el grafo raíz: lo hace {@code DatabaseInit}. Los subgrafos se publican con
 * {@link ServicioAutoria#publicar(Long)}, usando borradores reales, para que el
 * médico quede como autor y el contenido tenga versión, referencias en la raíz
 * y copia publicada.
 *
 * <p>
 * Es idempotente: si una cuenta existe por correo o un subgrafo ya tiene
 * borrador o grafo publicado, se omite. Pensado para una base de demo limpia;
 * para volver a sembrar desde cero hay que borrar los volúmenes de PostgreSQL y
 * Redis.
 */
@Service
public class SeedDemoService {

    private static final Logger log = LoggerFactory.getLogger(SeedDemoService.class);
    private static final String PREFIJO = "seed/demo/";

    private final SeedDemoProperties props;
    private final RepositorioUsuario usuarios;
    private final PasswordEncoder codificador;
    private final RepositorioBorradorSubgrafo borradores;
    private final RepositorioBorradorBaseRegla basesBorrador;
    private final RepositorioGrafoClinico grafos;
    private final ServicioAutoria autoria;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Inyecta las dependencias del seed. */
    public SeedDemoService(SeedDemoProperties props, RepositorioUsuario usuarios,
            PasswordEncoder codificador, RepositorioBorradorSubgrafo borradores,
            RepositorioBorradorBaseRegla basesBorrador, RepositorioGrafoClinico grafos,
            ServicioAutoria autoria) {
        this.props = props;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.borradores = borradores;
        this.basesBorrador = basesBorrador;
        this.grafos = grafos;
        this.autoria = autoria;
    }

    /** Siembra cuentas y subgrafos; todo o nada. */
    @Transactional
    public void sembrar() {
        UsuarioEntity medico = asegurarCuenta(props.getMedico(), Rol.MEDICO);
        asegurarCuenta(props.getPadre(), Rol.PADRE);
        SeedManifest manifiesto = leerManifiesto();

        int publicados = 0;
        for (SeedManifest.SubgrafoSeed subgrafo : manifiesto.subgrafos()) {
            if (publicarSubgrafo(subgrafo, medico)) {
                publicados++;
            }
        }
        log.info("Seed demo terminado: médico '{}', {} subgrafo(s) publicado(s).",
                medico.getNombre(), publicados);
    }

    /**
     * Crea la cuenta si no existe. No modifica una cuenta existente.
     */
    private UsuarioEntity asegurarCuenta(SeedDemoProperties.Cuenta datos, Rol rol) {
        if (datos == null || datos.getCorreo() == null || datos.getCorreo().isBlank()) {
            throw new IllegalStateException("el seed demo no configuró el correo de " + rol);
        }
        String correo = datos.getCorreo().trim().toLowerCase(Locale.ROOT);
        Optional<UsuarioEntity> existente = usuarios.findByCorreo(correo);
        if (existente.isPresent()) {
            if (existente.get().getRol() != rol) {
                throw new IllegalStateException("la cuenta " + correo + " ya existe con rol "
                        + existente.get().getRol() + "; el seed esperaba " + rol);
            }
            log.info("Seed demo: la cuenta '{}' ya existe.", correo);
            return existente.get();
        }
        if (datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new IllegalStateException("el seed demo no configuró el nombre de " + correo);
        }
        if (datos.getContrasena() == null || datos.getContrasena().length() < 8) {
            throw new IllegalStateException("el seed demo necesita una contraseña de al menos 8 "
                    + "caracteres para " + correo);
        }
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNombre(datos.getNombre().trim());
        usuario.setCorreo(correo);
        usuario.setHashContrasena(codificador.encode(datos.getContrasena()));
        usuario.setRol(rol);
        usuario.setInstitucion(rol == Rol.PADRE ? null : datos.getInstitucion());
        usuario.setActivo(true);
        usuario.setCreadoEn(Instant.now());
        usuarios.save(usuario);
        log.info("Seed demo: cuenta '{}' creada con rol {}.", correo, rol);
        return usuario;
    }

    /**
     * Publica un subgrafo si no existe. Devuelve false si ya estaba.
     */
    private boolean publicarSubgrafo(SeedManifest.SubgrafoSeed datos, UsuarioEntity medico) {
        GrafoClinicoJson grafo = GrafoClinicoParser.parseJsonToDocumento(
                leer(PREFIJO + datos.grafo()));
        String grafoId = grafo.graph() == null ? null : grafo.graph().id();
        if (grafoId == null || grafoId.isBlank()) {
            throw new IllegalStateException("el subgrafo del seed no declara 'id': " + datos.grafo());
        }
        if (borradores.existsByGrafoId(grafoId) || existePublicado(grafoId)) {
            log.info("Seed demo: '{}' ya existe; se omite.", grafoId);
            return false;
        }
        if (datos.puenteId() == null || datos.puenteId().isBlank()) {
            throw new IllegalStateException("el manifiesto no declara el puente de " + grafoId);
        }

        Instant ahora = Instant.now();
        BorradorSubgrafoEntity borrador = new BorradorSubgrafoEntity();
        borrador.setGrafoId(grafoId);
        borrador.setNombre(grafo.graph().label());
        borrador.setPuenteId(datos.puenteId());
        borrador.setCategoria(datos.categoria());
        borrador.setEtiquetaOpcion(datos.etiquetaOpcion());
        borrador.setAutor(medico.getNombre());
        borrador.setInstitucion(medico.getInstitucion());
        borrador.setAutorId(medico.getId());
        borrador.setDocumento(SeedGraphConverter.aLienzo(grafo));
        borrador.setEstado(EstadoBorrador.BORRADOR);
        borrador.setCreadoEn(ahora);
        borrador.setActualizadoEn(ahora);
        borrador = borradores.save(borrador);

        if (datos.reglas() != null && !datos.reglas().isBlank()) {
            BaseReglasJson base = BaseReglasParser.parseJsonToDocumento(
                    leer(PREFIJO + datos.reglas()));
            if (!grafoId.equals(base.grafoId())) {
                throw new IllegalStateException("la base " + datos.reglas()
                        + " no pertenece a " + grafoId);
            }
            BorradorBaseReglaEntity borradorBase = new BorradorBaseReglaEntity();
            borradorBase.setGrafoId(grafoId);
            borradorBase.setNombre("Reglas de " + borrador.getNombre());
            borradorBase.setDescripcion("Base de reglas sembrada para " + borrador.getNombre());
            borradorBase.setAutor(medico.getNombre());
            borradorBase.setInstitucion(medico.getInstitucion());
            borradorBase.setAutorId(medico.getId());
            borradorBase.setDocumento(SeedGraphConverter.aDocumentoReglas(base));
            borradorBase.setEstado(EstadoBorrador.BORRADOR);
            borradorBase.setCreadoEn(ahora);
            borradorBase.setActualizadoEn(ahora);
            basesBorrador.save(borradorBase);
        }

        autoria.publicar(borrador.getId());
        log.info("Seed demo: '{}' publicado con su base de reglas.", grafoId);
        return true;
    }

    private boolean existePublicado(String grafoId) {
        return grafos.findAll().stream()
                .map(GrafoClinicoEntity::getEstructura)
                .anyMatch(documento -> documento != null && documento.graph() != null
                        && grafoId.equals(documento.graph().id()));
    }

    private SeedManifest leerManifiesto() {
        try {
            SeedManifest manifiesto = mapper.readValue(leer(props.getManifiesto()),
                    SeedManifest.class);
            if (manifiesto == null || manifiesto.subgrafos() == null
                    || manifiesto.subgrafos().isEmpty()) {
                throw new IllegalStateException("el manifiesto del seed no tiene subgrafos");
            }
            return manifiesto;
        } catch (RuntimeException e) {
            throw new IllegalStateException("no se pudo leer el manifiesto del seed '"
                    + props.getManifiesto() + "': " + e.getMessage(), e);
        }
    }

    /** Lee un recurso del classpath como texto UTF-8. */
    private static String leer(String ruta) {
        ClassPathResource recurso = new ClassPathResource(ruta);
        try (InputStream entrada = recurso.getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo leer el recurso " + ruta, e);
        }
    }
}
