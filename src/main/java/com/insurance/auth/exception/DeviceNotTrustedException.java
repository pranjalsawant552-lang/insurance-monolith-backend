package com.insurance.auth.exception;

import org.springframework.http.HttpStatus;

public class DeviceNotTrustedException extends AuthException {
    public DeviceNotTrustedException() {
        super("This device is not trusted. Please verify via OTP to continue.", "DEVICE_NOT_TRUSTED", HttpStatus.FORBIDDEN);
    }
}
