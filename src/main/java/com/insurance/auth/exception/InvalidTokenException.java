package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends AuthException {
    public InvalidTokenException(String message) {
        super(message, "INVALID_TOKEN", HttpStatus.UNAUTHORIZED);
    }
}
