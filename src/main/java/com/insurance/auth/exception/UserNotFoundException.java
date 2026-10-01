package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AuthException {
    public UserNotFoundException(String message) {
        super(message, "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
