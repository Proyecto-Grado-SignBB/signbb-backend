package org.signbb.backend.seguridad;

import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.CredencialesInvalidasException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Entrega a los controladores el {@link UsuarioActual} de la petición: un
 * parámetro de ese tipo se llena con el principal que dejó {@link
 * ConvertidorJwtUsuario}. Sin sesión responde 401.
 */
public class ResolvedorUsuarioActual implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return UsuarioActual.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer mav,
            NativeWebRequest peticion, WebDataBinderFactory binder) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioActual usuario) {
            return usuario;
        }
        throw new CredencialesInvalidasException(ConvertidorJwtUsuario.SESION_TERMINADA);
    }
}
