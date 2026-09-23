package org.signbb.backend.cache;

/**
 * El cache Redis no está disponible para una operación que no puede
 * degradarse (guardar o buscar una sesión). Se responde 503 para que el
 * cliente sepa que es transitorio y reintente; caer a memoria crearía
 * inconsistencias (sesiones que aparecen y desaparecen según la instancia).
 */
public class CacheNoDisponibleException extends RuntimeException {

    public CacheNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
