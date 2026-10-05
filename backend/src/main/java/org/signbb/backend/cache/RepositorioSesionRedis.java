package org.signbb.backend.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.signbb.backend.navegacion.modelo.ContextoClinico;
import org.signbb.backend.navegacion.modelo.EstadoPaciente;
import org.signbb.backend.navegacion.modelo.Nodo;
import org.signbb.backend.navegacion.modelo.PasoRecorrido;
import org.signbb.backend.navegacion.modelo.PasoCausal;
import org.signbb.backend.navegacion.modelo.RepositorioSesion;
import org.signbb.backend.navegacion.modelo.ResultadoPaso;
import org.signbb.backend.navegacion.modelo.SesionTriaje;
import org.signbb.backend.navegacion.servicios.MotorNavegacion;
import org.signbb.backend.reglas.modelo.NivelAlerta;
import org.signbb.backend.reglas.modelo.ReglaClinica;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Almacén de sesiones en Redis (perfil {@code docker}), según el documento
 * de diseño: las sesiones temporales de los padres viven en la caché para
 * que una consulta en curso sobreviva reinicios del backend y pueda
 * compartirse entre instancias.
 *
 * <p>
 * Se persiste el estado reconstruible (respuestas acumuladas + referencias
 * por id, ver {@link SesionJson}) y el {@code ResultadoPaso} se reconstruye
 * al cargar resolviendo esas referencias contra el contenido clínico vigente
 * del {@link ContextoClinico}. Si Redis no está disponible, falla con
 * {@link CacheNoDisponibleException} (503): degradar a memoria crearía
 * inconsistencias entre sesiones que aparecen y desaparecen.
 */
@Component
@Profile("docker")
public class RepositorioSesionRedis implements RepositorioSesion {

    /** Prefijo de las claves de sesión en Redis. */
    public static final String PREFIJO_SESION = "signbb:sesion:";

    /** TTL de las sesiones: las consultas abandonadas expiran solas. */
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ContextoClinico contexto;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Inyecta el template de Redis y el contexto clínico para reconstruir pasos. */
    public RepositorioSesionRedis(StringRedisTemplate redis, ContextoClinico contexto) {
        this.redis = redis;
        this.contexto = contexto;
    }

    @Override
    public SesionTriaje crear(EstadoPaciente estado, ResultadoPaso pasoInicial) {
        SesionTriaje sesion = new SesionTriaje(UUID.randomUUID().toString(), estado, pasoInicial);
        guardar(sesion);
        return sesion;
    }

    @Override
    public SesionTriaje buscar(String id) {
        String json = leer(PREFIJO_SESION + id);
        if (json == null) {
            throw new RecursoNoEncontradoException("Recorrido no encontrado: " + id);
        }
        return reconstruir(mapper.readValue(json, SesionJson.class));
    }

    @Override
    public void guardar(SesionTriaje sesion) {
        escribir(PREFIJO_SESION + sesion.getId(),
                mapper.writeValueAsString(SesionJson.desde(sesion)));
    }

    /** Reconstruye la sesión desde su forma serializable, resolviendo referencias por id. */
    private SesionTriaje reconstruir(SesionJson json) {
        EstadoPaciente estado = new EstadoPaciente(json.id());
        for (Map.Entry<String, List<String>> e : json.respuestas().entrySet()) {
            estado.registrarRespuestas(e.getKey(), e.getValue());
        }
        if (json.reglasDisparadas() != null) {
            for (String idRegla : json.reglasDisparadas()) {
                estado.marcarReglaDisparada(idRegla);
            }
        }
        estado.sumarPuntaje(json.puntaje());
        if (json.historial() != null) {
            for (SesionJson.PasoRecorridoJson p : json.historial()) {
                estado.apilarPaso(new PasoRecorrido(p.idPregunta(), p.respuestas(),
                        p.respuestasPrevias(), p.reglasMarcadas()));
            }
        }
        return new SesionTriaje(json.id(), estado, reconstruirPaso(json.ultimoPaso()));
    }

    /**
     * Reconstruye el último paso resolviendo nodos y reglas contra el
     * contenido vigente. El desenlace de sistema
     * {@code MotorNavegacion.LIMITE_PREGUNTAS} no está en el grafo y se
     * resuelve por su id.
     */
    private ResultadoPaso reconstruirPaso(SesionJson.PasoJson paso) {
        if (ResultadoPaso.Estado.EN_CURSO.name().equals(paso.estado())) {
            Nodo pregunta = contexto.getGrafo().obtenerNodoPorId(paso.preguntaId())
                    .orElseThrow(() -> new IllegalStateException(
                            "la sesión referencia la pregunta '" + paso.preguntaId()
                                    + "' que ya no existe en el contenido clínico"));
            return ResultadoPaso.pregunta(pregunta);
        }

        Nodo destino = MotorNavegacion.LIMITE_PREGUNTAS.getId().equals(paso.destinoId())
                ? MotorNavegacion.LIMITE_PREGUNTAS
                : contexto.getGrafo().obtenerNodoPorId(paso.destinoId())
                        .orElseThrow(() -> new IllegalStateException(
                                "la sesión referencia el nodo '" + paso.destinoId()
                                        + "' que ya no existe en el contenido clínico"));
        NivelAlerta nivel = paso.nivel() == null ? NivelAlerta.NORMAL
                : NivelAlerta.valueOf(paso.nivel());

        List<ReglaClinica> razones = new ArrayList<>();
        for (String idRegla : paso.razonesIds()) {
            razones.add(contexto.getBase().obtenerRegla(idRegla));
        }
        List<PasoCausal> camino = new ArrayList<>();
        for (SesionJson.CaminoJson c : paso.caminoCausal()) {
            camino.add(new PasoCausal(
                    contexto.getGrafo().obtenerNodoPorId(c.nodoId()).orElseThrow(
                            () -> new IllegalStateException(
                                    "el camino causal referencia el nodo '" + c.nodoId()
                                            + "' que ya no existe en el contenido clínico")),
                    c.condicion()));
        }
        return ResultadoPaso.terminal(destino, nivel, razones, camino);
    }

    /** Lee una sesión; Redis caído se reporta como 503, no como sesión inexistente. */
    private String leer(String clave) {
        try {
            return redis.opsForValue().get(clave);
        } catch (DataAccessException e) {
            throw new CacheNoDisponibleException(
                    "el almacenamiento de sesiones no está disponible", e);
        }
    }

    /** Escribe una sesión con TTL; Redis caído se reporta como 503. */
    private void escribir(String clave, String json) {
        try {
            redis.opsForValue().set(clave, json, TTL);
        } catch (DataAccessException e) {
            throw new CacheNoDisponibleException(
                    "el almacenamiento de sesiones no está disponible", e);
        }
    }
}
