package com.insurance.auth.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateOtp(int length) {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) return "**@" + domain;
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1) + "@" + domain;
    }

    public String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 6) return "****";
        return mobile.substring(0, mobile.length() - 4).replaceAll("\\d", "*")
                + mobile.substring(mobile.length() - 4);
    }

    public String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
