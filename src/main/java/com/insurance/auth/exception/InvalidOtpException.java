package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidOtpException extends AuthException {
    public InvalidOtpException(String message) {
        super(message, "INVALID_OTP", HttpStatus.BAD_REQUEST);
    }
}
