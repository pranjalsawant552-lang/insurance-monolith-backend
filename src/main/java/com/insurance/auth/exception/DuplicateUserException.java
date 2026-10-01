package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class DuplicateUserException extends AuthException {
    public DuplicateUserException(String message) {
        super(message, "DUPLICATE_USER", HttpStatus.CONFLICT);
    }
}
