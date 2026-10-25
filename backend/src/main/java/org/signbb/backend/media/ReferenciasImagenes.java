package org.signbb.backend.media;

import java.util.HashSet;
import java.util.Set;

import org.signbb.backend.autoria.datos.entidades.BorradorSubgrafoEntity;
import org.signbb.backend.autoria.datos.repositorios.RepositorioBorradorSubgrafo;
import org.signbb.backend.navegacion.datos.entidades.GrafoClinicoEntity;
import org.signbb.backend.navegacion.datos.entidades.VersionContenidoEntity;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioGrafoClinico;
import org.signbb.backend.navegacion.datos.repositorios.RepositorioVersionContenido;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Calcula qué imágenes están referenciadas y cuáles son públicas.
 *
 * <p>
 * Una imagen es pública si aparece en un grafo publicado o en una versión del
 * contenido que todavía se conserva. Las imágenes que solo están en borradores
 * son privadas y solo se pueden ver desde autoría.
 */
@Component
public class ReferenciasImagenes {

    private final RepositorioBorradorSubgrafo borradores;
    private final RepositorioGrafoClinico grafos;
    private final RepositorioVersionContenido versiones;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final long VIGENCIA_CACHE_MS = 5 * 60 * 1000L;

    private volatile Set<String> publicadasCache;
    private volatile long expiraCache;

    /** Inyecta los repositorios donde pueden aparecer referencias a imágenes. */
    public ReferenciasImagenes(RepositorioBorradorSubgrafo borradores,
            RepositorioGrafoClinico grafos, RepositorioVersionContenido versiones) {
        this.borradores = borradores;
        this.grafos = grafos;
        this.versiones = versiones;
    }

    /** Imágenes usadas por cualquier borrador, grafo publicado o versión. */
    public Set<String> enUso() {
        Set<String> enUso = new HashSet<>();
        for (BorradorSubgrafoEntity b : borradores.findAll()) {
            enUso.addAll(nombresEn(b.getDocumento()));
            enUso.addAll(nombresEn(b.getCopiaPublicada()));
        }
        enUso.addAll(publicadas());
        return enUso;
    }

    /** Imágenes que pueden servirse por la ruta pública. */
    public Set<String> publicadas() {
        long ahora = System.currentTimeMillis();
        Set<String> cache = publicadasCache;
        if (cache == null || ahora > expiraCache) {
            synchronized (this) {
                cache = publicadasCache;
                if (cache == null || ahora > expiraCache) {
                    cache = calcularPublicadas();
                    publicadasCache = cache;
                    expiraCache = ahora + VIGENCIA_CACHE_MS;
                }
            }
        }
        return cache;
    }

    /** True si la imagen aparece en contenido publicado o en una versión conservada. */
    public boolean esPublicada(String nombre) {
        return nombre != null && publicadas().contains(nombre);
    }

    /** Hay grafos publicados; si no, la limpieza no puede decidir qué sobra. */
    public boolean hayGrafosPublicados() {
        return grafos.count() > 0;
    }

    /** Invalida la copia en memoria; se llama al publicar, despublicar o borrar versiones. */
    public void invalidar() {
        publicadasCache = null;
        expiraCache = 0;
    }

    private Set<String> calcularPublicadas() {
        Set<String> publicadas = new HashSet<>();
        for (GrafoClinicoEntity g : grafos.findAll()) {
            publicadas.addAll(nombresEn(g.getEstructura()));
        }
        for (VersionContenidoEntity v : versiones.findAll()) {
            publicadas.addAll(nombresEn(v.getGrafo()));
        }
        return publicadas;
    }

    private Set<String> nombresEn(Object documento) {
        return documento == null ? Set.of() : FormatoImagen.nombresEn(mapper.writeValueAsString(documento));
    }
}
