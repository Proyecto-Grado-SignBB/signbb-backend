package org.signbb.backend.mantenimiento;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.media.AlmacenImagenes;
import org.signbb.backend.media.AlmacenNoDisponibleException;
import org.signbb.backend.media.FormatoImagen;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Borra de Silo las imágenes que nadie usa.
 *
 * <p>
 * Una imagen está en uso si su nombre aparece en algún borrador (el lienzo o
 * la copia de lo publicado), en algún grafo publicado o en alguna versión del
 * contenido que todavía se conserva. Nunca se borra una imagen subida hace
 * menos de {@code GRACIA}: el médico la sube antes de guardar la pregunta, así
 * que por un rato todavía no la referencia nadie. Tampoco se toca nada cuyo
 * nombre no sea de los que genera el servidor.
 *
 * <p>
 * Ante la duda no borra: si no se puede listar, si no hay contenido publicado
 * en la base de datos o si falla al leer algún documento, termina sin borrar.
 */
@Component
public class LimpiadorImagenes {

    private static final Logger log = LoggerFactory.getLogger(LimpiadorImagenes.class);

    /** Tiempo mínimo que debe tener una imagen para poder borrarla. */
    public static final Duration GRACIA = Duration.ofHours(24);

    private final AlmacenImagenes almacen;
    private final RepositorioBorradorSubgrafo borradores;
    private final RepositorioGrafoClinico grafos;
    private final RepositorioVersionContenido versiones;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Inyecta el almacén de imágenes y los repositorios donde pueden estar referenciadas. */
    public LimpiadorImagenes(AlmacenImagenes almacen, RepositorioBorradorSubgrafo borradores,
            RepositorioGrafoClinico grafos, RepositorioVersionContenido versiones) {
        this.almacen = almacen;
        this.borradores = borradores;
        this.grafos = grafos;
        this.versiones = versiones;
    }

    /**
     * Borra las imágenes sin uso.
     *
     * @param ahora el momento de la limpieza
     * @return nombres de las imágenes borradas
     * @throws AlmacenNoDisponibleException si Silo no responde al listar
     */
    public List<String> limpiar(Instant ahora) {
        // Se lista primero: lo que se suba mientras se revisa no entra en esta pasada.
        List<AlmacenImagenes.Guardada> guardadas = almacen.listar();
        Instant limite = ahora.minus(GRACIA);
        List<String> candidatas = guardadas.stream()
                .filter(g -> FormatoImagen.nombreValido(g.nombre()))
                .filter(g -> g.modificadaEn().isBefore(limite))
                .map(AlmacenImagenes.Guardada::nombre)
                .toList();
        if (candidatas.isEmpty()) {
            log.info("Limpieza de imágenes: no hay imágenes con más de {} h para revisar ({} guardadas).",
                    GRACIA.toHours(), guardadas.size());
            return List.of();
        }
        if (grafos.count() == 0) {
            log.warn("Limpieza de imágenes cancelada: no hay grafos en la base de datos, así que no se puede "
                    + "saber qué imágenes se usan.");
            return List.of();
        }

        Set<String> enUso = nombresEnUso();
        List<String> borradas = new ArrayList<>();
        for (String nombre : candidatas) {
            if (enUso.contains(nombre)) {
                continue;
            }
            try {
                almacen.eliminar(nombre);
                borradas.add(nombre);
            } catch (AlmacenNoDisponibleException e) {
                log.warn("No se pudo borrar la imagen '{}': {}", nombre, e.getMessage());
            }
        }
        log.info("Limpieza de imágenes: se borraron {} de {} imágenes ({} en uso).", borradas.size(),
                guardadas.size(), enUso.size());
        return borradas;
    }

    private Set<String> nombresEnUso() {
        Set<String> enUso = new HashSet<>();
        for (BorradorSubgrafoEntity b : borradores.findAll()) {
            enUso.addAll(nombresEn(b.getDocumento()));
            enUso.addAll(nombresEn(b.getCopiaPublicada()));
        }
        for (GrafoClinicoEntity g : grafos.findAll()) {
            enUso.addAll(nombresEn(g.getEstructura()));
        }
        for (VersionContenidoEntity v : versiones.findAll()) {
            enUso.addAll(nombresEn(v.getGrafo()));
        }
        return enUso;
    }

    private Set<String> nombresEn(Object documento) {
        return documento == null ? Set.of() : FormatoImagen.nombresEn(mapper.writeValueAsString(documento));
    }
}
