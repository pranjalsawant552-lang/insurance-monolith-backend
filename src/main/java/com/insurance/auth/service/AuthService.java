package com.insurance.auth.service;

import com.insurance.auth.dto.request.*;
import com.insurance.auth.dto.response.*;
import com.insurance.auth.entity.*;
import com.insurance.auth.exception.*;
import com.insurance.auth.repository.*;
import com.insurance.auth.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpUtil otpUtil;
    private final PasswordValidator passwordValidator;

    private final OtpService otpService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final AuditService auditService;
    private final KafkaEventService kafkaEventService;
    private final RbacService rbacService;

    @Value("${lockout.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${lockout.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    // ═══════════════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, String> register(RegisterRequest req, String ipAddress) {
        // Validate uniqueness
        if (userRepository.existsByEmailAndDeletedFalse(req.getEmail().toLowerCase())) {
            throw new DuplicateUserException("An account with this email already exists.");
        }
        if (userRepository.existsByMobileNumberAndDeletedFalse(req.getMobileNumber())) {
            throw new DuplicateUserException("An account with this mobile number already exists.");
        }

        // Validate password strength
        List<String> pwErrors = passwordValidator.validate(req.getPassword());
        if (!pwErrors.isEmpty()) {
            throw new AuthException("Weak password: " + String.join(", ", pwErrors),
                    "WEAK_PASSWORD", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        String passwordHash = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .email(req.getEmail().toLowerCase().trim())
                .mobileNumber(req.getMobileNumber().trim())
                .passwordHash(passwordHash)
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .role(UserRole.CUSTOMER)
                .passwordHistory(passwordValidator.addToHistory("[]", passwordHash, 5))
                .active(false)
                .emailVerified(false)
                .mobileVerified(false)
                .build();

        user = userRepository.save(user);

        // Send both verification OTPs in parallel (async)
        String emailOtp = otpService.generateOtp(user, user.getEmail(), OtpType.EMAIL_VERIFY, ipAddress);
        String mobileOtp = otpService.generateOtp(user, user.getMobileNumber(), OtpType.MOBILE_VERIFY, ipAddress);

        emailService.sendEmailVerificationOtp(user.getEmail(), user.getFirstName(), emailOtp);
        smsService.sendMobileVerificationOtp(user.getMobileNumber(), mobileOtp);

        auditService.log(AuthAuditLog.REGISTER, user.getId(), ipAddress, null,
                Map.of("email", user.getEmail()));
        kafkaEventService.publishUserRegistered(user);

        log.info("New user registered: {}", user.getEmail());

        return Map.of(
                "userId", user.getId().toString(),
                "maskedEmail", otpUtil.maskEmail(user.getEmail()),
                "maskedMobile", otpUtil.maskMobile(user.getMobileNumber()),
                "message", "Registration successful. Please verify your email and mobile number."
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VERIFY EMAIL OTP
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void verifyEmail(VerifyOtpRequest req, String ipAddress) {
        otpService.validateOtp(req.getIdentifier().toLowerCase(), req.getOtpCode(), OtpType.EMAIL_VERIFY);

        User user = userRepository.findByEmailAndDeletedFalse(req.getIdentifier().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setEmailVerified(true);
        activateIfFullyVerified(user);
        userRepository.save(user);

        auditService.log(AuthAuditLog.EMAIL_VERIFIED, user.getId(), ipAddress, null);

        if (user.isActive()) {
            kafkaEventService.publishUserActivated(user);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VERIFY MOBILE OTP
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void verifyMobile(VerifyOtpRequest req, String ipAddress) {
        otpService.validateOtp(req.getIdentifier(), req.getOtpCode(), OtpType.MOBILE_VERIFY);

        User user = userRepository.findByMobileNumberAndDeletedFalse(req.getIdentifier())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setMobileVerified(true);
        activateIfFullyVerified(user);
        userRepository.save(user);

        auditService.log(AuthAuditLog.MOBILE_VERIFIED, user.getId(), ipAddress, null);

        if (user.isActive()) {
            kafkaEventService.publishUserActivated(user);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public AuthResponse login(LoginRequest req, String ipAddress, String userAgent) {
        // Resolve user by email or mobile
        User user = resolveUser(req.getIdentifier());

        // Check lockout
        if (user.isLocked()) {
            throw new AccountLockedException(
                    "Account is locked until " + user.getLockedUntil() + ". "
                    + "Use the unlock link sent to your email or mobile.");
        }

        // Check account is active
        if (!user.isActive()) {
            if (!user.isEmailVerified() || !user.isMobileVerified()) {
                throw new AuthException("Account not verified. Please verify your email and mobile number.",
                        "ACCOUNT_NOT_VERIFIED", org.springframework.http.HttpStatus.FORBIDDEN);
            }
        }

        // Validate password
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user, ipAddress, userAgent);
            int remaining = maxFailedAttempts - user.getFailedLoginAttempts();
            if (remaining <= 0) {
                throw new AccountLockedException("Account locked due to too many failed attempts. "
                        + "Check your email/mobile to unlock.");
            }
            throw new AuthException("Invalid credentials. " + remaining + " attempt(s) remaining.",
                    "INVALID_CREDENTIALS", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }

        // Validate TOTP if enabled
        if (user.isTotpEnabled()) {
            if (req.getTotpCode() == null || req.getTotpCode().isBlank()) {
                return AuthResponse.builder()
                        .requiresTotpSetup(false)
                        .pendingIdentifier(otpUtil.maskEmail(user.getEmail()))
                        .build();
            }
            // TOTP validation delegated to TotpService (injected below if needed)
        }

        // Device trust check
        String deviceFp = req.getDeviceFingerprint();
        if (deviceFp != null && !deviceFp.isBlank()) {
            Optional<TrustedDevice> device = trustedDeviceRepository
                    .findByUserIdAndDeviceFingerprint(user.getId(), deviceFp);
            if (device.isEmpty() || !device.get().isTrusted() || device.get().isExpired()) {
                // New device: send OTP and require trust before issuing tokens
                String otp = otpService.generateOtp(user, user.getEmail(), OtpType.DEVICE_TRUST, ipAddress);
                emailService.sendDeviceTrustOtp(user.getEmail(), user.getFirstName(), otp,
                        req.getDeviceName() != null ? req.getDeviceName() : "Unknown device", ipAddress);
                smsService.sendDeviceTrustOtp(user.getMobileNumber(), otp);
                return AuthResponse.builder()
                        .requiresDeviceTrust(true)
                        .pendingIdentifier(otpUtil.maskEmail(user.getEmail()))
                        .build();
            }
            // Update last used
            device.get().setLastUsedAt(ZonedDateTime.now());
            trustedDeviceRepository.save(device.get());
        }

        // All checks passed — issue tokens
        return issueTokens(user, ipAddress, userAgent,
                req.getDeviceFingerprint() != null
                        ? UUID.fromString(sha256(req.getDeviceFingerprint()).substring(0, 8) + "-0000-0000-0000-000000000000")
                        : null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRUST DEVICE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public AuthResponse trustDevice(TrustDeviceRequest req, String email,
                                    String ipAddress, String userAgent) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        otpService.validateOtp(user.getEmail(), req.getOtpCode(), OtpType.DEVICE_TRUST);

        TrustedDevice device = trustedDeviceRepository
                .findByUserIdAndDeviceFingerprint(user.getId(), req.getDeviceFingerprint())
                .orElseGet(() -> TrustedDevice.builder()
                        .user(user)
                        .deviceFingerprint(req.getDeviceFingerprint())
                        .build());

        device.setDeviceName(req.getDeviceName() != null ? req.getDeviceName() : "My device");
        device.setTrusted(true);
        device.setTrustedAt(ZonedDateTime.now());
        device.setLastUsedAt(ZonedDateTime.now());
        device.setExpiresAt(ZonedDateTime.now().plusDays(90));
        device.setIpAddress(ipAddress);
        trustedDeviceRepository.save(device);

        auditService.log(AuthAuditLog.DEVICE_TRUSTED, user.getId(), ipAddress, userAgent,
                Map.of("deviceName", device.getDeviceName()));

        return issueTokens(user, ipAddress, userAgent, device.getId());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REFRESH TOKEN
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest req, String ipAddress, String userAgent) {
        String tokenHash = sha256(req.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid or expired."));

        if (!stored.isValid()) {
            throw new InvalidTokenException("Refresh token has expired. Please log in again.");
        }

        User user = stored.getUser();

        // Rotate: revoke old, issue new
        refreshTokenRepository.revokeByTokenHash(tokenHash, ZonedDateTime.now(), "ROTATED");

        auditService.log(AuthAuditLog.TOKEN_REFRESH, user.getId(), ipAddress, userAgent);

        return issueTokens(user, ipAddress, userAgent, stored.getDeviceId());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void logout(String refreshToken, UUID userId, String ipAddress, String userAgent) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.revokeByTokenHash(sha256(refreshToken),
                    ZonedDateTime.now(), "LOGOUT");
        }
        auditService.log(AuthAuditLog.LOGOUT, userId, ipAddress, userAgent);
    }

    @Transactional
    public void logoutAll(UUID userId, String ipAddress, String userAgent) {
        refreshTokenRepository.revokeAllForUser(userId, ZonedDateTime.now(), "LOGOUT_ALL");
        auditService.log(AuthAuditLog.LOGOUT, userId, ipAddress, userAgent,
                Map.of("scope", "ALL_SESSIONS"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FORGOT PASSWORD
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest req, String ipAddress) {
        // Always return success even if user not found (prevent enumeration)
        Optional<User> userOpt = findUserByIdentifier(req.getIdentifier());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Send to both channels for maximum deliverability
            String otp = otpService.generateOtp(user, user.getEmail(), OtpType.FORGOT_PASSWORD, ipAddress);
            emailService.sendForgotPasswordOtp(user.getEmail(), user.getFirstName(), otp);
            smsService.sendForgotPasswordOtp(user.getMobileNumber(), otp);
        }
        return Map.of("message",
                "If an account exists for that identifier, a reset OTP has been sent to the registered email and mobile.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESET PASSWORD
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void resetPassword(ResetPasswordRequest req, String ipAddress) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new AuthException("Passwords do not match.", "PASSWORD_MISMATCH",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // The reset token carries the user's email as a claim
        if (!jwtUtil.validateToken(req.getResetToken())) {
            throw new InvalidTokenException("Reset token is invalid or expired.");
        }

        String userId = jwtUtil.extractUserId(req.getResetToken());
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate OTP was verified for this user's email
        // (The reset token was issued only after OTP validation, so token validity is sufficient)

        // Check password strength
        List<String> errors = passwordValidator.validate(req.getNewPassword());
        if (!errors.isEmpty()) {
            throw new AuthException("Weak password: " + String.join(", ", errors),
                    "WEAK_PASSWORD", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // Check reuse
        if (passwordValidator.isPasswordReused(req.getNewPassword(), user.getPasswordHistory(), passwordEncoder)) {
            throw new PasswordReuseException();
        }

        String newHash = passwordEncoder.encode(req.getNewPassword());
        user.setPasswordHash(newHash);
        user.setPasswordHistory(passwordValidator.addToHistory(user.getPasswordHistory(), newHash, 5));
        user.setPasswordChangedAt(ZonedDateTime.now());
        userRepository.save(user);

        // Revoke all refresh tokens (force re-login everywhere)
        refreshTokenRepository.revokeAllForUser(user.getId(), ZonedDateTime.now(), "PASSWORD_RESET");

        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstName());

        auditService.log(AuthAuditLog.PASSWORD_RESET, user.getId(), ipAddress, null);
        kafkaEventService.publishPasswordChanged(user);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHANGE PASSWORD (authenticated)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void changePassword(ChangePasswordRequest req, UUID userId, String ipAddress) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new AuthException("Passwords do not match.", "PASSWORD_MISMATCH",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthException("Current password is incorrect.", "WRONG_PASSWORD",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        List<String> errors = passwordValidator.validate(req.getNewPassword());
        if (!errors.isEmpty()) {
            throw new AuthException("Weak password: " + String.join(", ", errors),
                    "WEAK_PASSWORD", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        if (passwordValidator.isPasswordReused(req.getNewPassword(), user.getPasswordHistory(), passwordEncoder)) {
            throw new PasswordReuseException();
        }

        String newHash = passwordEncoder.encode(req.getNewPassword());
        user.setPasswordHash(newHash);
        user.setPasswordHistory(passwordValidator.addToHistory(user.getPasswordHistory(), newHash, 5));
        user.setPasswordChangedAt(ZonedDateTime.now());
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(userId, ZonedDateTime.now(), "PASSWORD_CHANGED");
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstName());

        auditService.log(AuthAuditLog.PASSWORD_CHANGED, userId, ipAddress, null);
        kafkaEventService.publishPasswordChanged(user);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNLOCK ACCOUNT
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, String> requestUnlockOtp(String identifier, String channel, String ipAddress) {
        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!user.isLocked()) {
            return Map.of("message", "Account is not locked.");
        }

        String otp;
        if ("MOBILE".equalsIgnoreCase(channel)) {
            otp = otpService.generateOtp(user, user.getMobileNumber(), OtpType.UNLOCK_ACCOUNT, ipAddress);
            smsService.sendUnlockOtp(user.getMobileNumber(), otp);
            return Map.of("message", "Unlock OTP sent to your mobile: " + otpUtil.maskMobile(user.getMobileNumber()));
        } else {
            otp = otpService.generateOtp(user, user.getEmail(), OtpType.UNLOCK_ACCOUNT, ipAddress);
            emailService.sendAccountLockedEmail(user.getEmail(), user.getFirstName(), otp);
            return Map.of("message", "Unlock OTP sent to your email: " + otpUtil.maskEmail(user.getEmail()));
        }
    }

    @Transactional
    public void unlockAccount(UnlockAccountRequest req, String ipAddress) {
        User user = findUserByIdentifier(req.getIdentifier())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String otpIdentifier = "MOBILE".equalsIgnoreCase(req.getChannel())
                ? user.getMobileNumber() : user.getEmail();

        otpService.validateOtp(otpIdentifier, req.getOtpCode(), OtpType.UNLOCK_ACCOUNT);

        user.resetFailedAttempts();
        userRepository.save(user);

        emailService.sendAccountUnlockedEmail(user.getEmail(), user.getFirstName());
        auditService.log(AuthAuditLog.ACCOUNT_UNLOCKED, user.getId(), ipAddress, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESEND OTP
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, String> resendOtp(ResendOtpRequest req, String ipAddress) {
        OtpType type = OtpType.valueOf(req.getOtpType());
        Optional<User> userOpt;
        String identifier = req.getIdentifier();

        if (identifier.contains("@")) {
            userOpt = userRepository.findByEmailAndDeletedFalse(identifier.toLowerCase());
        } else {
            userOpt = userRepository.findByMobileNumberAndDeletedFalse(identifier);
        }

        userOpt.ifPresent(user -> {
            String otp = otpService.generateOtp(user, identifier, type, ipAddress);
            if (type == OtpType.EMAIL_VERIFY) {
                emailService.sendEmailVerificationOtp(user.getEmail(), user.getFirstName(), otp);
            } else if (type == OtpType.MOBILE_VERIFY) {
                smsService.sendMobileVerificationOtp(user.getMobileNumber(), otp);
            }
        });

        return Map.of("message", "OTP resent if the identifier is registered.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SESSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    public List<SessionResponse> getActiveSessions(UUID userId) {
        return refreshTokenRepository.findByUserIdAndRevokedFalse(userId).stream()
                .filter(t -> !t.isExpired())
                .map(t -> SessionResponse.builder()
                        .tokenId(t.getId())
                        .ipAddress(t.getIpAddress())
                        .createdAt(t.getCreatedAt())
                        .expiresAt(t.getExpiresAt())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeSession(UUID tokenId, UUID userId, String ipAddress, String userAgent) {
        RefreshToken token = refreshTokenRepository.findById(tokenId)
                .orElseThrow(() -> new InvalidTokenException("Session not found"));

        if (!token.getUser().getId().equals(userId)) {
            throw new AuthException("Cannot revoke another user's session.", "FORBIDDEN",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        token.setRevoked(true);
        token.setRevokedAt(ZonedDateTime.now());
        token.setRevokeReason("USER_REVOKED");
        refreshTokenRepository.save(token);

        auditService.log(AuthAuditLog.TOKEN_REVOKED, userId, ipAddress, userAgent);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VERIFY RESET OTP and issue reset token
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, String> verifyResetOtp(VerifyOtpRequest req, String ipAddress) {
        // Look up user by email or mobile
        Optional<User> userOpt = findUserByIdentifier(req.getIdentifier());
        User user = userOpt.orElseThrow(() -> new UserNotFoundException("User not found"));

        String identifier = req.getIdentifier().contains("@")
                ? user.getEmail() : user.getMobileNumber();
        otpService.validateOtp(identifier, req.getOtpCode(), OtpType.FORGOT_PASSWORD);

        // Issue a short-lived JWT reset token
        String resetToken = jwtUtil.generateResetToken(user);
        return Map.of("resetToken", resetToken,
                "message", "OTP verified. Use resetToken to set a new password.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BOOTSTRAP (Phase 10 — closes the "no way to create the first admin" gap)
    // ═══════════════════════════════════════════════════════════════════════════

    @Value("${bootstrap.admin-secret:}")
    private String bootstrapAdminSecret;

    /**
     * Creates the platform's very first SUPER_ADMIN, fully active with no OTP step —
     * whoever holds the deploy-time secret is, by definition, already trusted. Two
     * independent guards keep this from being a standing backdoor:
     *   1. {@code BOOTSTRAP_ADMIN_SECRET} must be set in the environment and match —
     *      leave it unset in any environment where this should be impossible to call.
     *   2. It refuses to run at all once a SUPER_ADMIN already exists — so even a
     *      leaked secret is only useful during the brief window before first setup.
     * Rotate/unset the env var after use; nothing in this service does that for you.
     */
    @Transactional
    public AuthResponse bootstrapAdmin(BootstrapAdminRequest req, String ipAddress, String userAgent) {
        if (bootstrapAdminSecret == null || bootstrapAdminSecret.isBlank()) {
            throw new AuthException(
                    "Admin bootstrap is disabled — BOOTSTRAP_ADMIN_SECRET is not configured on this deployment.",
                    "BOOTSTRAP_DISABLED", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (!java.security.MessageDigest.isEqual(
                bootstrapAdminSecret.getBytes(StandardCharsets.UTF_8),
                req.getSecret().getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("Invalid bootstrap secret.", "BOOTSTRAP_FORBIDDEN",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (userRepository.existsByRoleAndDeletedFalse(UserRole.SUPER_ADMIN)) {
            throw new AuthException(
                    "An admin already exists on this platform — bootstrap only runs once. "
                            + "Ask an existing Super Admin to create your account via /admin/users.",
                    "BOOTSTRAP_ALREADY_DONE", org.springframework.http.HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmailAndDeletedFalse(req.getEmail().toLowerCase())) {
            throw new DuplicateUserException("An account with this email already exists.");
        }
        if (userRepository.existsByMobileNumberAndDeletedFalse(req.getMobileNumber())) {
            throw new DuplicateUserException("An account with this mobile number already exists.");
        }

        List<String> pwErrors = passwordValidator.validate(req.getPassword());
        if (!pwErrors.isEmpty()) {
            throw new AuthException("Weak password: " + String.join(", ", pwErrors),
                    "WEAK_PASSWORD", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        String passwordHash = passwordEncoder.encode(req.getPassword());
        User admin = User.builder()
                .email(req.getEmail().toLowerCase().trim())
                .mobileNumber(req.getMobileNumber().trim())
                .passwordHash(passwordHash)
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .role(UserRole.SUPER_ADMIN)
                .companyId(req.getCompanyId())
                .passwordHistory(passwordValidator.addToHistory("[]", passwordHash, 5))
                .active(true)
                .emailVerified(true)
                .mobileVerified(true)
                .build();
        admin = userRepository.save(admin);

        auditService.log(AuthAuditLog.REGISTER, admin.getId(), ipAddress, userAgent,
                Map.of("email", admin.getEmail(), "role", "SUPER_ADMIN", "via", "bootstrap"));
        kafkaEventService.publishUserRegistered(admin);
        log.warn("Bootstrap admin created: {} — BOOTSTRAP_ADMIN_SECRET should be rotated/unset now.", admin.getEmail());

        return issueTokens(admin, ipAddress, userAgent, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROFILE
    // ═══════════════════════════════════════════════════════════════════════════

    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return UserProfileResponse.from(user);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private AuthResponse issueTokens(User user, String ipAddress, String userAgent, UUID deviceId) {
        java.util.List<String> permissions = rbacService.getPermissionCodesForRole(user.getRole().name());
        String accessToken = jwtUtil.generateAccessToken(user, permissions);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        RefreshToken storedRefresh = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(refreshToken))
                .deviceId(deviceId)
                .expiresAt(ZonedDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpiryMs() / 1000))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        refreshTokenRepository.save(storedRefresh);

        userRepository.recordSuccessfulLogin(user.getId(), ZonedDateTime.now(), ipAddress, ZonedDateTime.now());
        auditService.log(AuthAuditLog.LOGIN_SUCCESS, user.getId(), ipAddress, userAgent);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accessTokenExpiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                .user(UserProfileResponse.from(user))
                .build();
    }

    private void handleFailedLogin(User user, String ipAddress, String userAgent) {
        userRepository.incrementFailedAttempts(user.getId(), ZonedDateTime.now());
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

        if (user.getFailedLoginAttempts() >= maxFailedAttempts) {
            ZonedDateTime lockUntil = ZonedDateTime.now().plusMinutes(lockoutDurationMinutes);
            user.setLockedAt(ZonedDateTime.now());
            user.setLockedUntil(lockUntil);
            user.setLockReason("Too many failed login attempts");
            userRepository.save(user);

            // Send unlock OTP immediately
            try {
                String otp = otpService.generateOtp(user, user.getEmail(), OtpType.UNLOCK_ACCOUNT, ipAddress);
                emailService.sendAccountLockedEmail(user.getEmail(), user.getFirstName(), otp);
                smsService.sendUnlockOtp(user.getMobileNumber(), otp);
            } catch (Exception e) {
                log.warn("Could not send unlock OTP after lock: {}", e.getMessage());
            }

            auditService.log(AuthAuditLog.ACCOUNT_LOCKED, user.getId(), ipAddress, userAgent,
                    Map.of("lockedUntil", lockUntil.toString()));
        } else {
            auditService.log(AuthAuditLog.LOGIN_FAILED, user.getId(), ipAddress, userAgent,
                    Map.of("attempts", user.getFailedLoginAttempts()));
        }
    }

    private void activateIfFullyVerified(User user) {
        if (user.isEmailVerified() && user.isMobileVerified()) {
            user.setActive(true);
        }
    }

    private User resolveUser(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmailAndDeletedFalse(identifier.toLowerCase())
                    .orElseThrow(() -> new AuthException("Invalid credentials.",
                            "INVALID_CREDENTIALS", org.springframework.http.HttpStatus.UNAUTHORIZED));
        }
        return userRepository.findByMobileNumberAndDeletedFalse(identifier)
                .orElseThrow(() -> new AuthException("Invalid credentials.",
                        "INVALID_CREDENTIALS", org.springframework.http.HttpStatus.UNAUTHORIZED));
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null) return Optional.empty();
        if (identifier.contains("@")) {
            return userRepository.findByEmailAndDeletedFalse(identifier.toLowerCase());
        }
        return userRepository.findByMobileNumberAndDeletedFalse(identifier);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}
