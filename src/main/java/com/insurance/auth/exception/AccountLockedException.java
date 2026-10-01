package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends AuthException {
    public AccountLockedException(String message) {
        super(message, "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN);
    }
}
