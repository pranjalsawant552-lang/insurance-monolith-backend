package com.insurance.auth.service;

import com.insurance.auth.repository.OtpRepository;
import com.insurance.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final OtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // Run every night at 02:00
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        int deleted = otpRepository.deleteExpiredOtps(ZonedDateTime.now());
        log.info("Cleanup: deleted {} expired OTP records", deleted);
    }

    // Run every night at 02:30
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.deleteExpired(ZonedDateTime.now().minusDays(1));
        log.info("Cleanup: deleted {} expired refresh tokens", deleted);
    }

    // Every hour: log active session count for monitoring
    @Scheduled(fixedRate = 3_600_000)
    public void reportActiveSessions() {
        log.debug("Cleanup scheduler heartbeat — {}", ZonedDateTime.now());
    }
}
