package com.enterprise.ai.security.config;

import com.enterprise.ai.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration for the AI Orchestrator API.
 * 
 * <p>This configuration sets up a stateless, JWT-based authentication system.
 * CSRF protection is intentionally disabled because:</p>
 * <ul>
 *   <li>The API is stateless and does not use session cookies</li>
 *   <li>Authentication is performed via JWT tokens in the Authorization header</li>
 *   <li>JWT tokens are not automatically sent by browsers like cookies are</li>
 *   <li>CORS is properly configured to restrict cross-origin requests</li>
 * </ul>
 * 
 * Role-based access control:
 * <ul>
 *   <li>ADMIN: Full access to all admin APIs</li>
 *   <li>OPERATOR: Access to audit logs, sessions, and monitoring</li>
 *   <li>USER: Access to chat and public endpoints only</li>
 * </ul>
 * 
 * @see <a href="https://owasp.org/www-community/vulnerabilities/Cross-Site_Request_Forgery_(CSRF)">OWASP CSRF</a>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configures the security filter chain for JWT-based stateless authentication.
     * CSRF is disabled as this is a stateless REST API using JWT tokens in headers.
     *
     * Special handling for async/streaming requests to prevent "response already committed" errors.
     */
    @Bean
    @SuppressWarnings("java:S4502") // CSRF disabled intentionally for stateless JWT API
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no auth required
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/ollama/health").permitAll()
                        .requestMatchers("/api/v2/scenario/test").permitAll()
                        .requestMatchers("/api/v2/scenario/tests/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()  // Allow error page (prevents "response already committed")

                        // Streaming endpoints - authenticated users
                        // These endpoints use SSE and async dispatch, must be handled specially
                        .requestMatchers("/api/v2/chat/stream").authenticated()
                        .requestMatchers("/api/v2/chat/events/**").authenticated()
                        
                        // Admin-only endpoints - scenarios, settings, URL whitelist, cache
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Only handle if response is not committed (not in streaming mode)
                            if (!response.isCommitted()) {
                                response.sendError(401, "Unauthorized");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // Only handle if response is not committed (not in streaming mode)
                            if (!response.isCommitted()) {
                                response.sendError(403, "Access Denied");
                            }
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
