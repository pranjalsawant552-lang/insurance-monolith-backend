package com.insurance.auth.service;

import com.insurance.auth.dto.response.TotpSetupResponse;
import com.insurance.auth.entity.User;
import com.insurance.auth.exception.AuthException;
import com.insurance.auth.exception.InvalidOtpException;
import com.insurance.auth.exception.UserNotFoundException;
import com.insurance.auth.repository.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    private final UserRepository userRepository;

    @Value("${app.company-name}")
    private String companyName;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final TimeProvider timeProvider = new SystemTimeProvider();

    // ── Setup: generate secret + QR ──────────────────────────────────────────

    @Transactional
    public TotpSetupResponse setupTotp(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isTotpEnabled()) {
            throw new AuthException("TOTP is already enabled for this account.",
                    "TOTP_ALREADY_ENABLED", HttpStatus.BAD_REQUEST);
        }

        String secret = secretGenerator.generate();
        user.setTotpSecret(secret);
        userRepository.save(user);

        String qrUri = generateQrUri(user.getEmail(), secret);

        try {
            QrData qrData = new QrData.Builder()
                    .label(user.getEmail())
                    .secret(secret)
                    .issuer(companyName)
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();

            ZxingPngQrGenerator qrGenerator = new ZxingPngQrGenerator();
            byte[] qrImageBytes = qrGenerator.generate(qrData);
            String qrBase64 = Base64.getEncoder().encodeToString(qrImageBytes);

            return TotpSetupResponse.builder()
                    .secret(secret)
                    .qrCodeUri(qrUri)
                    .qrCodeBase64("data:image/png;base64," + qrBase64)
                    .build();

        } catch (QrGenerationException e) {
            log.warn("QR generation failed, returning URI only: {}", e.getMessage());
            return TotpSetupResponse.builder()
                    .secret(secret)
                    .qrCodeUri(qrUri)
                    .build();
        }
    }

    // ── Enable: verify first code to activate ────────────────────────────────

    @Transactional
    public void enableTotp(UUID userId, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getTotpSecret() == null) {
            throw new AuthException("TOTP setup not initiated. Call /auth/totp/setup first.",
                    "TOTP_NOT_SETUP", HttpStatus.BAD_REQUEST);
        }

        if (!verifyCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidOtpException("Invalid TOTP code. Please check your authenticator app.");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);

        log.info("TOTP enabled for user {}", userId);
    }

    // ── Disable TOTP ─────────────────────────────────────────────────────────

    @Transactional
    public void disableTotp(UUID userId, String totpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!user.isTotpEnabled()) {
            throw new AuthException("TOTP is not enabled.", "TOTP_NOT_ENABLED", HttpStatus.BAD_REQUEST);
        }

        if (!verifyCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidOtpException("Invalid TOTP code.");
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);

        log.info("TOTP disabled for user {}", userId);
    }

    // ── Verify a TOTP code (used during login) ────────────────────────────────

    public void validateTotpForLogin(User user, String code) {
        if (!user.isTotpEnabled() || user.getTotpSecret() == null) return;
        if (code == null || code.isBlank()) {
            throw new AuthException("TOTP code is required.", "TOTP_REQUIRED", HttpStatus.UNAUTHORIZED);
        }
        if (!verifyCode(user.getTotpSecret(), code)) {
            throw new InvalidOtpException("Invalid TOTP code.");
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private boolean verifyCode(String secret, String code) {
        CodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6),
                timeProvider
        );
        // Allow 1 time-step window (±30 seconds) for clock skew
        ((DefaultCodeVerifier) verifier).setTimePeriod(30);
        ((DefaultCodeVerifier) verifier).setAllowedTimePeriodDiscrepancy(1);
        return verifier.isValidCode(secret, code);
    }

    private String generateQrUri(String email, String secret) {
        try {
            String encodedIssuer = java.net.URLEncoder.encode(companyName,
                    java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            String encodedLabel = java.net.URLEncoder.encode(email,
                    java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            return "otpauth://totp/" + encodedIssuer + ":" + encodedLabel
                    + "?secret=" + secret
                    + "&issuer=" + encodedIssuer
                    + "&algorithm=SHA1&digits=6&period=30";
        } catch (Exception e) {
            return "";
        }
    }
}
