package com.insurance.monolith.config;

import com.insurance.auth.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * MONOLITH CONVERSION NOTE — this is the single most important behavior
 * change in the whole conversion, and worth reading in full even if you
 * skip everything else in MONOLITH-CONVERSION-NOTES.md.
 *
 * The original architecture split JWT verification in two:
 *   - api-gateway verified the caller's JWT once, at the edge, then set
 *     trusted X-User-Id / X-User-Role / X-User-Permissions / X-Company-Id /
 *     X-Branch-Id headers on the request it forwarded downstream (see the
 *     old api-gateway's JwtAuthFilter — not copied into this monolith).
 *   - Every one of the 11 non-auth services then had its own HeaderAuthFilter
 *     that TRUSTED those headers blindly and populated Spring Security's
 *     context from them — correct only because the gateway had already
 *     stripped any client-supplied X-User-* headers and the services were
 *     (supposedly) unreachable except through the gateway.
 *
 * There is no gateway anymore. If those 11 HeaderAuthFilter classes had been
 * carried over unchanged, a caller hitting this monolith directly could set
 * X-User-Role: SUPER_ADMIN on a raw HTTP request and every @PreAuthorize
 * check in the codebase would trust it — a full authentication bypass, not
 * a cosmetic gap. All 11 HeaderAuthFilter classes and all 12 per-module
 * SecurityConfig classes were deleted for exactly this reason.
 *
 * In their place: auth-service's own JwtAuthFilter — which does real JWT
 * signature/expiry verification via JwtUtil, not header-trusting — is now
 * applied globally, in front of every module's controllers, by the one
 * SecurityFilterChain below. It sets identical Spring Security authorities
 * (ROLE_<role> plus one authority per permission code) and identical
 * request attributes (userId/companyId/branchId) that every controller
 * across every module already reads — so no controller code had to change.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final com.insurance.auth.security.UserDetailsServiceImpl userDetailsService;

    /**
     * Union of every module's former PUBLIC_ENDPOINTS array. Health/swagger
     * entries were identical (copy-pasted) across all 12 services, so they
     * collapse to one copy each; auth's own public auth flow endpoints,
     * payment's webhook receiver, and product's two public pricing/catalog
     * endpoints are each preserved from their original module.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            // health / docs — identical across every former service
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",

            // auth-service — endpoints that by definition run before a caller
            // has a JWT at all
            "/auth/register",
            "/auth/bootstrap-admin",
            "/auth/login",
            "/auth/verify-email",
            "/auth/verify-mobile",
            "/auth/resend-otp",
            "/auth/forgot-password",
            "/auth/verify-reset-otp",
            "/auth/reset-password",
            "/auth/unlock/request",
            "/auth/unlock/confirm",
            "/auth/refresh",
            "/auth/trust-device",

            // payment-service — gateway webhook callback, never carries a
            // platform JWT (it's called by the payment provider, not a user)
            "/payments/webhook/**",

            // product-service — public catalog browsing / pricing preview
            "/products/featured",
            "/products/premium-preview"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; frame-ancestors 'none'"))
                .frameOptions(fo -> fo.deny())
                .referrerPolicy(rp -> rp.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://*.insurance.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Device-Fingerprint",
                "X-Forwarded-For", "X-Real-IP", "Accept"
        ));
        config.setExposedHeaders(List.of("Authorization", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
