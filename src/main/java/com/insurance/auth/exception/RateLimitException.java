package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class RateLimitException extends AuthException {
    public RateLimitException(String message) {
        super(message, "RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS);
    }
}
