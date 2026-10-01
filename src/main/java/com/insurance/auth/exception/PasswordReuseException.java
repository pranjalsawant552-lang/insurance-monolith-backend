package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class PasswordReuseException extends AuthException {
    public PasswordReuseException() {
        super("Password was recently used. Please choose a different password.", "PASSWORD_REUSED", HttpStatus.BAD_REQUEST);
    }
}
