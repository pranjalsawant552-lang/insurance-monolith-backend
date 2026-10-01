package com.insurance.monolith.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MONOLITH CONVERSION NOTE: ported from the old api-gateway's
 * SecurityHeadersFilter (a WebFlux GlobalFilter, which only works in a
 * reactive gateway app — this monolith is a standard Spring MVC servlet
 * app) to an equivalent OncePerRequestFilter. Same headers, same intent:
 * every response gets these regardless of which module's controller
 * produced it, since the gateway that used to guarantee that is gone.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");

        filterChain.doFilter(request, response);

        response.setHeader("Server", null);
        response.setHeader("X-Powered-By", null);
    }
}
