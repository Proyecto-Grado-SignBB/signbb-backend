package org.signbb.backend.autoria.servicios;

import java.util.List;

import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoMapper;
import org.signbb.backend.navegacion.modelo.GrafoClinico;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Utilidades compartidas por {@link PublicadorSubgrafo} y
 * {@link PublicadorRegla}: componer y validar el contenido clínico antes de
 * guardarlo, y ejecutar la recarga del cache solo cuando la transacción
 * confirma.
 */
public final class PublicacionContenido {

    private PublicacionContenido() {
    }

    /**
     * Compone raíz, subgrafos y bases de reglas y reconstruye los TAD; si algo
     * falla, se responde al médico con un 422.
     *
     * @param contexto prefijo del mensaje (p. ej. "El subgrafo no se pudo publicar")
     */
    public static void validarComposicion(String contexto, GrafoClinicoJson raiz,
            List<GrafoClinicoJson> subgrafos, List<BaseReglasJson> reglas) {
        try {
            CompositorGrafoClinico.Contenido contenido = CompositorGrafoClinico.componer(raiz, subgrafos, reglas);
            GrafoClinico grafo = GrafoClinicoMapper.desde(contenido.grafo());
            BaseReglasMapper.desde(contenido.reglas(), grafo);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ContenidoInvalidoException(contexto + ": " + e.getMessage(), List.of());
        }
    }

    /** Ejecuta la acción cuando la transacción confirma, o ya si no hay transacción. */
    public static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
