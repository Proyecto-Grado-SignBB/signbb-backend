package org.signbb.backend.seguridad;

import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Usuarios fijos para los tests sin Spring Security: dos médicos, un
 * supervisor, y un resolvedor que entrega siempre el mismo usuario a los
 * controladores probados con {@code MockMvcBuilders.standaloneSetup}.
 */
public final class UsuariosPrueba {

    /** Médico autor de los borradores de los tests. */
    public static final UsuarioActual MEDICO_A =
            new UsuarioActual(10L, "Dra. A", "a@signbb.local", Rol.MEDICO, "Fundación Canguro");
    /** Otro médico, que no es autor. */
    public static final UsuarioActual MEDICO_B =
            new UsuarioActual(20L, "Dr. B", "b@signbb.local", Rol.MEDICO, "Hospital B");
    /** Supervisor que no es autor. */
    public static final UsuarioActual SUPERVISOR =
            new UsuarioActual(30L, "Dra. S", "s@signbb.local", Rol.MEDICO_SUPERVISOR, "Fundación Canguro");

    private UsuariosPrueba() {
    }

    /** Resolvedor que entrega siempre {@code usuario} como {@link UsuarioActual}. */
    public static HandlerMethodArgumentResolver resolvedor(UsuarioActual usuario) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parametro) {
                return UsuarioActual.class.equals(parametro.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer mav,
                    NativeWebRequest peticion, WebDataBinderFactory binder) {
                return usuario;
            }
        };
    }
}
