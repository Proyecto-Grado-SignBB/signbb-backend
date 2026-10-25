package org.signbb.backend.autoria.servicios;

import org.signbb.backend.navegacion.datos.serializacion.CompositorGrafoClinico;
import org.signbb.backend.seguridad.modelo.UsuarioActual;

/**
 * Quién puede leer y quién puede editar un borrador (subgrafo o base de
 * reglas), según la cuenta de su autor:
 * <ul>
 * <li>Leer: el autor o un supervisor.</li>
 * <li>Editar: solo el autor.</li>
 * <li>Un borrador sin autor (de antes de las cuentas) solo lo lee un
 * supervisor y nadie lo edita.</li>
 * </ul>
 */
public final class PoliticaAccesoBorradores {

    /** Mensaje cuando se intenta usar el grafo raíz en el módulo de reglas. */
    public static final String REGLAS_RAIZ_SIN_BASE =
            "El grafo raíz no tiene una base de reglas editable.";

    private PoliticaAccesoBorradores() {
    }

    /** True si el id corresponde al grafo raíz del sistema. */
    public static boolean esBaseRaiz(String grafoId) {
        return CompositorGrafoClinico.ID_RAIZ.equals(grafoId);
    }

    /** True si el usuario puede ver el borrador. */
    public static boolean puedeLeer(Long autorId, UsuarioActual usuario) {
        return usuario.esSupervisor() || esAutor(autorId, usuario);
    }

    /** True si el usuario puede editar el borrador. */
    public static boolean puedeEditar(Long autorId, UsuarioActual usuario) {
        return esAutor(autorId, usuario);
    }

    /** True si el usuario es el autor del borrador. */
    public static boolean esAutor(Long autorId, UsuarioActual usuario) {
        return autorId != null && autorId.equals(usuario.id());
    }
}
