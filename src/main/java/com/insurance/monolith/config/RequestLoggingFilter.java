package com.insurance.monolith.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * MONOLITH CONVERSION NOTE: ported from the old api-gateway's
 * RequestLoggingFilter. Stamps every request with an X-Request-Id and puts
 * it in the logging MDC as "traceId" — every module's logback pattern
 * already references %X{traceId} (see application.yml's logging.pattern),
 * it just never had anything populating it once requests left the gateway.
 *
 * This is a genuine (partial) improvement over the original distributed
 * architecture, not just a like-for-like port: previously this ID only
 * existed at the edge — each of the 12 services logged with no shared
 * correlation ID at all, meaning debugging a request that touched, say,
 * policy → risk-fraud → claims meant manually correlating timestamps
 * across 3 separate log streams. In this monolith one ID now threads
 * through every module's log lines for that request, in one log stream,
 * because it's one process. It does not, by itself, fix the deeper gap the
 * README flagged (no structured logging/alerting pipeline) — see
 * MONOLITH-CONVERSION-NOTES.md.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(TRACE_ID_KEY, requestId);

        long start = System.currentTimeMillis();
        log.info("→ REQ [{}] {} {} from {}", requestId, request.getMethod(), request.getRequestURI(),
                clientIp(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("← RES [{}] {} {}ms status={}", requestId, request.getRequestURI(), duration,
                    response.getStatus());
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
