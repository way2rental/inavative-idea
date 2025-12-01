package com.enterprise.ai.security.jwt;

import com.enterprise.ai.common.context.RequestContext;
import com.enterprise.ai.common.context.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter with global user context propagation.
 * 
 * SECURITY CRITICAL:
 * - Extracts JWT from Authorization header
 * - Populates Spring SecurityContext
 * - Populates RequestContextHolder with userId, orgId, roles
 * - Clears RequestContextHolder at end of request to prevent thread-leak
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEFAULT_ORG = "DEFAULT_ORG";
    
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            final String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(7);
            final String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (!jwtService.isTokenExpired(jwt)) {
                    List<String> roles = jwtService.extractRoles(jwt);
                    List<SimpleGrantedAuthority> authorities = roles != null
                            ? roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()
                            : List.of();

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // CRITICAL: Populate RequestContextHolder for global access
                    populateRequestContext(request, jwt, username, roles);
                }
            }

            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error processing JWT token", e);
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Always clear context to prevent thread-leak in Tomcat thread pool
            RequestContextHolder.clear();
        }
    }

    /**
     * Populate RequestContextHolder from JWT claims.
     * This ensures user identity is available globally without passing as parameters.
     */
    private void populateRequestContext(HttpServletRequest request, String jwt, String username, List<String> roles) {
        try {
            // Extract orgId from JWT if present, otherwise use default
            String orgId = jwtService.extractOrgId(jwt);
            if (orgId == null || orgId.isBlank()) {
                orgId = DEFAULT_ORG;
                log.debug("No orgId in JWT for user {}, using default: {}", username, DEFAULT_ORG);
            }

            // Build context from JWT claims
            RequestContext context = RequestContext.builder()
                    .userId(username)
                    .orgId(orgId)
                    .tenantId(orgId)  // Same as orgId for backward compatibility
                    .roles(roles)
                    .role(roles != null && !roles.isEmpty() ? roles.get(0) : null)
                    .ipAddress(getClientIpAddress(request))
                    .sessionId(request.getSession(false) != null ? request.getSession().getId() : null)
                    .requestTimestamp(System.currentTimeMillis())
                    .build();

            RequestContextHolder.set(context);
            log.debug("RequestContext populated for user: {}, orgId: {}, roles: {}", 
                    username, orgId, roles);

        } catch (Exception e) {
            log.warn("Failed to populate RequestContext for user {}: {}", username, e.getMessage());
        }
    }

    /**
     * Extract client IP address, considering proxies and load balancers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
