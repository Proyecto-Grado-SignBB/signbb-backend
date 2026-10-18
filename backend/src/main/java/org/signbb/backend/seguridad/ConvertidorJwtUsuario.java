package org.signbb.backend.seguridad;

import java.util.List;

import org.signbb.backend.seguridad.modelo.UsuarioActual;
import org.signbb.backend.seguridad.servicios.ServicioCuentas;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Convierte un JWT ya validado en la autenticación de la petición. Busca la
 * cuenta por el {@code sub}: si no existe o está inactiva el token deja de
 * servir (401) aunque no haya vencido; lo mismo si la cuenta cerró sus
 * sesiones después de que se emitió el token. El rol sale de la cuenta
 * guardada, no del token, así un cambio de rol aplica de inmediato. El principal queda
 * como {@link UsuarioActual}.
 */
@Component
public class ConvertidorJwtUsuario implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Mensaje del 401 cuando la cuenta del token ya no sirve. */
    static final String SESION_TERMINADA = "Tu sesión terminó. Vuelve a ingresar.";

    private final ServicioCuentas cuentas;

    /** Inyecta el servicio de cuentas. */
    public ConvertidorJwtUsuario(ServicioCuentas cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long id = idDe(jwt);
        UsuarioActual usuario = id == null ? null : cuentas.usuarioActivo(id, jwt.getIssuedAt()).orElse(null);
        if (usuario == null) {
            throw new InvalidBearerTokenException(SESION_TERMINADA);
        }
        return UsernamePasswordAuthenticationToken.authenticated(usuario, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name())));
    }

    /** El id de la cuenta en el {@code sub}, o {@code null} si no es un número. */
    private static Long idDe(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
