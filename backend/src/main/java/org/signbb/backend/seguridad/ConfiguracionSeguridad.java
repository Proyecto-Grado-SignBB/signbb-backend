package org.signbb.backend.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.signbb.backend.seguridad.modelo.Rol;
import org.signbb.backend.seguridad.servicios.ServicioTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Reglas de acceso de la API. Sin estado: cada petición trae su JWT en
 * {@code Authorization: Bearer}, sin cookies ni CSRF.
 *
 * <ul>
 * <li>Públicas: preflight {@code OPTIONS}, registro e ingreso, imágenes
 * ({@code /media}) y la documentación (Swagger y {@code /v3/api-docs}).</li>
 * <li>{@code /api/recorridos/**}: PADRE.</li>
 * <li>{@code /api/usuarios/**}, {@code /api/autoria/revisiones} y aprobar,
 * rechazar o despublicar un subgrafo: MEDICO_SUPERVISOR.</li>
 * <li>El resto de {@code /api/autoria/**}: MEDICO o MEDICO_SUPERVISOR.</li>
 * <li>Cualquier otra ruta: con sesión.</li>
 * </ul>
 *
 * <p>
 * El 401 y el 403 salen en JSON con el formato {@code {mensaje, avisos}} que
 * ya entiende el front.
 */
@Configuration
public class ConfiguracionSeguridad {

    /** Largo mínimo del secreto para HS256 (256 bits). */
    static final int MINIMO_SECRETO = 32;

    private static final String SUPERVISOR = Rol.MEDICO_SUPERVISOR.name();

    /** Cadena de filtros con las reglas de acceso. */
    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, ConvertidorJwtUsuario convertidor,
            CorsConfigurationSource cors) throws Exception {
        AuthenticationEntryPoint sinSesion = (peticion, respuesta, error) -> {
            respuesta.setHeader("WWW-Authenticate", "Bearer");
            escribir(respuesta, HttpServletResponse.SC_UNAUTHORIZED,
                    "Necesitas ingresar para hacer esto. Si ya ingresaste, tu sesión terminó.");
        };
        AccessDeniedHandler sinPermiso = (peticion, respuesta, error) -> escribir(respuesta,
                HttpServletResponse.SC_FORBIDDEN, "No tienes permiso para hacer esto.");

        http.csrf(c -> c.disable())
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro").permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers("/api/recorridos", "/api/recorridos/**").hasRole(Rol.PADRE.name())
                        .requestMatchers("/api/usuarios", "/api/usuarios/**",
                                "/api/autoria/revisiones", "/api/autoria/revisiones/**").hasRole(SUPERVISOR)
                        .requestMatchers(HttpMethod.POST, "/api/autoria/subgrafos/*/aprobar",
                                "/api/autoria/subgrafos/*/rechazar",
                                "/api/autoria/subgrafos/*/despublicar").hasRole(SUPERVISOR)
                        .requestMatchers("/api/autoria/**").hasAnyRole(Rol.MEDICO.name(), SUPERVISOR)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(sinSesion)
                        .accessDeniedHandler(sinPermiso))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(sinSesion)
                        .accessDeniedHandler(sinPermiso));
        return http.build();
    }

    /**
     * Orígenes del front que pueden llamar a la API. Acepta patrones
     * ({@code http://localhost:*}) y se configura con
     * {@code signbb.seguridad.cors.origenes}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${signbb.seguridad.cors.origenes}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }

    /** Hash de contraseñas con BCrypt. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Clave HS256 de los tokens, desde {@code signbb.seguridad.jwt.secreto}. */
    @Bean
    public SecretKey claveJwt(@Value("${signbb.seguridad.jwt.secreto}") String secreto) {
        return clave(secreto);
    }

    /** Firma los tokens que emite {@code ServicioTokens}. */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return codificador(claveJwt);
    }

    /** Valida firma, vencimiento y emisor de los tokens recibidos. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey claveJwt) {
        return decodificador(claveJwt);
    }

    /**
     * La clave HS256 de un secreto.
     *
     * @throws IllegalStateException si el secreto tiene menos de 32 bytes
     */
    public static SecretKey clave(String secreto) {
        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMO_SECRETO) {
            throw new IllegalStateException("signbb.seguridad.jwt.secreto debe tener al menos "
                    + MINIMO_SECRETO + " bytes (SIGNBB_JWT_SECRETO).");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** Codificador HS256 con la clave. */
    public static JwtEncoder codificador(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    /** Decodificador HS256 con la clave, que exige el emisor de {@link ServicioTokens}. */
    public static JwtDecoder decodificador(SecretKey clave) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(clave)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(ServicioTokens.EMISOR));
        return decodificador;
    }

    /** Escribe un error de seguridad en el formato {@code {mensaje, avisos}}. */
    private static void escribir(HttpServletResponse respuesta, int estado, String mensaje) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json");
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write("{\"mensaje\":\"" + mensaje + "\",\"avisos\":[]}");
    }
}
