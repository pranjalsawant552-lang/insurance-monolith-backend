package com.insurance.auth.service;

import com.insurance.auth.entity.OtpRecord;
import com.insurance.auth.entity.OtpType;
import com.insurance.auth.entity.User;
import com.insurance.auth.exception.InvalidOtpException;
import com.insurance.auth.exception.RateLimitException;
import com.insurance.auth.repository.OtpRepository;
import com.insurance.auth.util.OtpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final OtpUtil otpUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.expiry-minutes:10}")
    private int expiryMinutes;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    // ── Generate & store OTP ──────────────────────────────────────────────────

    @Transactional
    public String generateOtp(User user, String identifier, OtpType otpType, String ipAddress) {
        // Rate limit: max 5 OTPs per identifier per hour
        String rateLimitKey = "otp:rate:" + identifier + ":" + otpType.name();
        String countStr = redisTemplate.opsForValue().get(rateLimitKey);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);
        if (count >= 5) {
            throw new RateLimitException("Too many OTP requests. Please try again in an hour.");
        }

        // Cooldown: prevent spam within 60 seconds
        String cooldownKey = "otp:cooldown:" + identifier + ":" + otpType.name();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new RateLimitException("Please wait before requesting a new OTP.");
        }

        // Invalidate all previous OTPs for this identifier + type
        otpRepository.invalidateAllForIdentifier(identifier, otpType);

        // Generate and save new OTP
        String code = otpUtil.generateOtp(otpLength);
        OtpRecord record = OtpRecord.builder()
                .user(user)
                .identifier(identifier)
                .otpCode(code)
                .otpType(otpType)
                .expiresAt(ZonedDateTime.now().plusMinutes(expiryMinutes))
                .ipAddress(ipAddress)
                .build();
        otpRepository.save(record);

        // Set rate limit and cooldown in Redis
        redisTemplate.opsForValue().increment(rateLimitKey);
        redisTemplate.expire(rateLimitKey, 1, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(cooldownKey, "1", resendCooldownSeconds, TimeUnit.SECONDS);

        log.info("OTP generated for identifier={} type={}", identifier, otpType);
        return code;
    }

    // ── Validate OTP ──────────────────────────────────────────────────────────

    @Transactional
    public void validateOtp(String identifier, String code, OtpType otpType) {
        OtpRecord record = otpRepository
                .findTopByIdentifierAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(identifier, otpType)
                .orElseThrow(() -> new InvalidOtpException("No active OTP found. Please request a new one."));

        if (record.isExpired()) {
            throw new InvalidOtpException("OTP has expired. Please request a new one.");
        }

        if (record.isMaxAttemptsReached()) {
            throw new InvalidOtpException("Maximum OTP attempts reached. Please request a new one.");
        }

        // Increment attempts before checking correctness (prevents brute force)
        record.setAttempts(record.getAttempts() + 1);
        otpRepository.save(record);

        if (!record.getOtpCode().equals(code)) {
            int remaining = maxAttempts - record.getAttempts();
            if (remaining <= 0) {
                throw new InvalidOtpException("Invalid OTP. Maximum attempts reached. Please request a new one.");
            }
            throw new InvalidOtpException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // Mark as used
        record.setUsed(true);
        record.setUsedAt(ZonedDateTime.now());
        otpRepository.save(record);

        log.info("OTP validated for identifier={} type={}", identifier, otpType);
    }
}
