package com.insurance.monolith.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Handles OptimisticLockingFailureException (thrown by Hibernate when a
 * @Version check fails — see the V24 migration and the @Version fields
 * added to Customer/Policy/Claim/ExtractionResult/Document/
 * AgentCommission/PaymentTransaction) with a clean 409 Conflict instead of
 * an unhandled 500. @Order(HIGHEST_PRECEDENCE) so this runs before any of
 * the 11 per-module GlobalExceptionHandler beans' generic
 * @ExceptionHandler(Exception.class) catch-all — those are unscoped
 * (@RestControllerAdvice with no basePackages), so without an explicit
 * order Spring's pick between 11 otherwise-identical generic handlers is
 * effectively arbitrary; this one specific handler is what should win for
 * this specific exception, regardless of that.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OptimisticLockExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "errorCode", "CONCURRENT_UPDATE_CONFLICT",
                "message", "This record was changed by someone else while you were editing it. "
                        + "Please refresh and try again."
        ));
    }
}
