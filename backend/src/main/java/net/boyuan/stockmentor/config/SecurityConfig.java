package net.boyuan.stockmentor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.service.AppUserDetailsService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityConfig.StockMentorCorsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {
    private static final List<String> DEFAULT_CORS_ALLOWED_ORIGINS = List.of(
            "http://localhost:8081",
            "http://127.0.0.1:8081"
    );

    private final AppUserDetailsService appUserDetailsService;
    private final ObjectMapper objectMapper;
    private final StockMentorCorsProperties corsProperties;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint apiAuthenticationEntryPoint = apiAuthenticationEntryPoint();
        AccessDeniedHandler apiAccessDeniedHandler = apiAccessDeniedHandler();

        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/health/database").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(apiAuthenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler)
                )
                .userDetailsService(appUserDetailsService)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, "X-Admin-Token"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (request, response, exception) -> writeSecurityError(
                response,
                HttpStatus.UNAUTHORIZED,
                "Authentication is required or the sign-in details were not accepted."
        );
    }

    private AccessDeniedHandler apiAccessDeniedHandler() {
        return (request, response, exception) -> writeSecurityError(
                response,
                HttpStatus.FORBIDDEN,
                "This account does not have access to this API."
        );
    }

    private void writeSecurityError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", status.value(),
                "message", message
        ));
    }

    private List<String> allowedOrigins() {
        List<String> configuredOrigins = exactEnvironmentAllowedOrigins();
        if (configuredOrigins.isEmpty()) {
            configuredOrigins = corsProperties.allowedOrigins();
        }

        List<String> allowedOrigins = configuredOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> !"*".equals(origin))
                .distinct()
                .toList();

        return allowedOrigins.isEmpty() ? DEFAULT_CORS_ALLOWED_ORIGINS : allowedOrigins;
    }

    private List<String> exactEnvironmentAllowedOrigins() {
        String rawOrigins = environment.getProperty("STOCKMENTOR_CORS_ALLOWED_ORIGINS");
        if (rawOrigins == null || rawOrigins.isBlank()) {
            rawOrigins = System.getenv("STOCKMENTOR_CORS_ALLOWED_ORIGINS");
        }
        if (rawOrigins == null || rawOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @ConfigurationProperties(prefix = "stockmentor.cors")
    public record StockMentorCorsProperties(List<String> allowedOrigins) {
        public StockMentorCorsProperties {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }
}
