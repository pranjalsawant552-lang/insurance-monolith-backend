package com.insurance.auth.controller;

import com.insurance.auth.dto.request.*;
import com.insurance.auth.dto.response.*;
import com.insurance.auth.entity.OtpType;
import com.insurance.auth.service.*;
import com.insurance.auth.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, OTP, password, sessions, TOTP")
public class AuthController {

    private final AuthService authService;
    private final TotpService totpService;
    private final RequestUtil requestUtil;

    // ── Registration ──────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
               description = "Creates account and sends OTP to both email and mobile for verification")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest request) {

        Map<String, String> result = authService.register(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Registration successful. Please verify your email and mobile."));
    }

    @PostMapping("/bootstrap-admin")
    @Operation(summary = "One-time creation of the platform's first Super Admin",
               description = "Only works when BOOTSTRAP_ADMIN_SECRET is configured on the deployment AND no "
                       + "SUPER_ADMIN exists yet — refuses to run a second time. Not a substitute for /admin/users "
                       + "once you have your first admin logged in.")
    public ResponseEntity<ApiResponse<AuthResponse>> bootstrapAdmin(
            @Valid @RequestBody BootstrapAdminRequest req,
            HttpServletRequest request) {
        AuthResponse result = authService.bootstrapAdmin(
                req, requestUtil.extractIpAddress(request), request.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Admin account created. Rotate BOOTSTRAP_ADMIN_SECRET now."));
    }

    // ── OTP Verification ──────────────────────────────────────────────────────

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address via OTP")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyOtpRequest req,
            HttpServletRequest request) {

        req.setOtpType(OtpType.EMAIL_VERIFY.name());
        authService.verifyEmail(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully."));
    }

    @PostMapping("/verify-mobile")
    @Operation(summary = "Verify mobile number via OTP")
    public ResponseEntity<ApiResponse<Void>> verifyMobile(
            @Valid @RequestBody VerifyOtpRequest req,
            HttpServletRequest request) {

        req.setOtpType(OtpType.MOBILE_VERIFY.name());
        authService.verifyMobile(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Mobile number verified successfully."));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP (with cooldown enforcement)")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendOtp(
            @Valid @RequestBody ResendOtpRequest req,
            HttpServletRequest request) {

        Map<String, String> result = authService.resendOtp(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login with email/mobile + password",
               description = "Returns JWT tokens. May return requiresDeviceTrust=true for new devices, or requiresTotp=true if MFA is enabled.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request) {

        AuthResponse response = authService.login(
                req,
                requestUtil.extractIpAddress(request),
                requestUtil.extractUserAgent(request));

        return ResponseEntity.ok(ApiResponse.success(response, "Login successful."));
    }

    // ── Device Trust ──────────────────────────────────────────────────────────

    @PostMapping("/trust-device")
    @Operation(summary = "Trust a new device using OTP sent after unrecognised login")
    public ResponseEntity<ApiResponse<AuthResponse>> trustDevice(
            @Valid @RequestBody TrustDeviceRequest req,
            @RequestParam String email,
            HttpServletRequest request) {

        AuthResponse response = authService.trustDevice(
                req, email,
                requestUtil.extractIpAddress(request),
                requestUtil.extractUserAgent(request));

        return ResponseEntity.ok(ApiResponse.success(response, "Device trusted. Login successful."));
    }

    // ── Token Refresh ─────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and get new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest req,
            HttpServletRequest request) {

        AuthResponse response = authService.refreshToken(
                req,
                requestUtil.extractIpAddress(request),
                requestUtil.extractUserAgent(request));

        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed."));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Logout current session", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest req,
            HttpServletRequest request) {

        UUID userId = extractUserId(request);
        authService.logout(
                req != null ? req.getRefreshToken() : null,
                userId,
                requestUtil.extractIpAddress(request),
                requestUtil.extractUserAgent(request));

        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully."));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout all sessions on all devices", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletRequest request) {

        authService.logoutAll(
                extractUserId(request),
                requestUtil.extractIpAddress(request),
                requestUtil.extractUserAgent(request));

        return ResponseEntity.ok(ApiResponse.success(null, "All sessions revoked."));
    }

    // ── Forgot / Reset Password ───────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset OTP (sent to email + mobile)")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req,
            HttpServletRequest request) {

        Map<String, String> result = authService.forgotPassword(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/verify-reset-otp")
    @Operation(summary = "Verify forgot-password OTP to get a reset token")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyResetOtp(
            @Valid @RequestBody VerifyOtpRequest req,
            HttpServletRequest request) {

        Map<String, String> result = authService.verifyResetOtp(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set new password using reset token obtained after OTP verification")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req,
            HttpServletRequest request) {

        authService.resetPassword(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully. Please log in."));
    }

    // ── Change Password (authenticated) ───────────────────────────────────────

    @PostMapping("/change-password")
    @Operation(summary = "Change password (requires current password)", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            HttpServletRequest request) {

        authService.changePassword(req, extractUserId(request), requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed. All other sessions have been logged out."));
    }

    // ── Account Unlock ────────────────────────────────────────────────────────

    @PostMapping("/unlock/request")
    @Operation(summary = "Request an unlock OTP for a locked account")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestUnlock(
            @RequestParam String identifier,
            @RequestParam(defaultValue = "EMAIL") String channel,
            HttpServletRequest request) {

        Map<String, String> result = authService.requestUnlockOtp(
                identifier, channel, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/unlock/confirm")
    @Operation(summary = "Confirm unlock OTP to re-activate locked account")
    public ResponseEntity<ApiResponse<Void>> confirmUnlock(
            @Valid @RequestBody UnlockAccountRequest req,
            HttpServletRequest request) {

        authService.unlockAccount(req, requestUtil.extractIpAddress(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Account unlocked successfully. You can now log in."));
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(HttpServletRequest request) {
        UserProfileResponse profile = authService.getProfile(extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    @GetMapping("/sessions")
    @Operation(summary = "List all active sessions", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions(HttpServletRequest request) {
        List<SessionResponse> sessions = authService.getActiveSessions(extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @DeleteMapping("/sessions/{tokenId}")
    @Operation(summary = "Revoke a specific session by token ID", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable UUID tokenId,
            HttpServletRequest request) {

        authService.revokeSession(
                tokenId,
                extractUserId(request),
                requestUtil.extractIpAddress(request),
                requestUtil.extractUserAgent(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Session revoked."));
    }

    // ── TOTP / MFA ────────────────────────────────────────────────────────────

    @PostMapping("/totp/setup")
    @Operation(summary = "Initiate TOTP setup — returns secret and QR code", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setupTotp(HttpServletRequest request) {
        TotpSetupResponse result = totpService.setupTotp(extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(result, "Scan the QR code with your authenticator app, then call /totp/enable."));
    }

    @PostMapping("/totp/enable")
    @Operation(summary = "Enable TOTP after scanning QR code (verify first code)", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> enableTotp(
            @Valid @RequestBody TotpVerifyRequest req,
            HttpServletRequest request) {

        totpService.enableTotp(extractUserId(request), req.getTotpCode());
        return ResponseEntity.ok(ApiResponse.success(null, "Two-factor authentication enabled."));
    }

    @PostMapping("/totp/disable")
    @Operation(summary = "Disable TOTP (requires current TOTP code)", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> disableTotp(
            @Valid @RequestBody TotpVerifyRequest req,
            HttpServletRequest request) {

        totpService.disableTotp(extractUserId(request), req.getTotpCode());
        return ResponseEntity.ok(ApiResponse.success(null, "Two-factor authentication disabled."));
    }

    // ── Health (internal) ─────────────────────────────────────────────────────

    @GetMapping("/health")
    @Operation(summary = "Auth service health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "auth-service"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID extractUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new com.insurance.auth.exception.AuthException(
                    "User ID not found in token.", "UNAUTHORIZED",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return UUID.fromString(userId);
    }
}
