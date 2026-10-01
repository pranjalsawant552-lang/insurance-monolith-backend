package com.insurance.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final StringRedisTemplate redisTemplate;

    // ── Dashboard summary ─────────────────────────────────────────────────────

    public Map<String, Object> getDashboardSummary(String companyId) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Read counters from Redis (incremented by Kafka consumers)
        summary.put("totalPolicies",        getLong(companyId, "policies:total"));
        summary.put("activePolicies",       getLong(companyId, "policies:active"));
        summary.put("lapsedPolicies",       getLong(companyId, "policies:lapsed"));
        summary.put("totalCustomers",       getLong(companyId, "customers:total"));
        summary.put("kycVerifiedCustomers", getLong(companyId, "customers:kyc_verified"));
        summary.put("totalClaims",          getLong(companyId, "claims:total"));
        summary.put("openClaims",           getLong(companyId, "claims:open"));
        summary.put("settledClaims",        getLong(companyId, "claims:settled"));
        summary.put("totalAgents",          getLong(companyId, "agents:total"));
        summary.put("totalPremiumCollected",getDecimal(companyId, "payments:premium_total"));
        summary.put("totalClaimsSettled",   getDecimal(companyId, "payments:claims_settled"));
        summary.put("generatedAt",          ZonedDateTime.now().toString());

        return summary;
    }

    // ── Policy analytics ──────────────────────────────────────────────────────

    public Map<String, Object> getPolicyAnalytics(String companyId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byType", Map.of(
                "LIFE",   getLong(companyId, "policies:type:LIFE"),
                "HEALTH", getLong(companyId, "policies:type:HEALTH"),
                "MOTOR",  getLong(companyId, "policies:type:MOTOR")
        ));
        data.put("byStatus", Map.of(
                "PENDING",     getLong(companyId, "policies:status:PENDING"),
                "ACTIVE",      getLong(companyId, "policies:status:ACTIVE"),
                "LAPSED",      getLong(companyId, "policies:status:LAPSED"),
                "SURRENDERED", getLong(companyId, "policies:status:SURRENDERED"),
                "MATURED",     getLong(companyId, "policies:status:MATURED"),
                "CANCELLED",   getLong(companyId, "policies:status:CANCELLED")
        ));
        data.put("totalPremium", getDecimal(companyId, "payments:premium_total"));
        return data;
    }

    // ── Claims analytics ──────────────────────────────────────────────────────

    public Map<String, Object> getClaimsAnalytics(String companyId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byStatus", Map.of(
                "SUBMITTED",               getLong(companyId, "claims:status:SUBMITTED"),
                "UNDER_REVIEW",            getLong(companyId, "claims:status:UNDER_REVIEW"),
                "APPROVED",                getLong(companyId, "claims:status:APPROVED"),
                "REJECTED",                getLong(companyId, "claims:status:REJECTED"),
                "SETTLED",                 getLong(companyId, "claims:status:SETTLED")
        ));
        data.put("byType", Map.of(
                "LIFE",   getLong(companyId, "claims:type:LIFE"),
                "HEALTH", getLong(companyId, "claims:type:HEALTH"),
                "MOTOR",  getLong(companyId, "claims:type:MOTOR")
        ));
        data.put("fraudFlagged", getLong(companyId, "claims:fraud_flagged"));
        data.put("totalSettledAmount", getDecimal(companyId, "payments:claims_settled"));
        return data;
    }

    // ── Payment analytics ─────────────────────────────────────────────────────

    public Map<String, Object> getPaymentAnalytics(String companyId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalPremiumCollected", getDecimal(companyId, "payments:premium_total"));
        data.put("totalClaimsDisbursed",  getDecimal(companyId, "payments:claims_settled"));
        data.put("totalTransactions",     getLong(companyId, "payments:count"));
        data.put("failedPayments",        getLong(companyId, "payments:failed_count"));
        data.put("overdueCount",          getLong(companyId, "payments:overdue_count"));
        return data;
    }

    // ── Event counter methods (called by Kafka consumers) ────────────────────

    public void increment(String companyId, String metric) {
        String key = "analytics:" + companyId + ":" + metric;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 90, TimeUnit.DAYS);
    }

    public void incrementAmount(String companyId, String metric, String amount) {
        try {
            double val = Double.parseDouble(amount);
            String key = "analytics:" + companyId + ":" + metric;
            redisTemplate.opsForValue().increment(key, val);
            redisTemplate.expire(key, 90, TimeUnit.DAYS);
        } catch (NumberFormatException e) {
            log.warn("Invalid amount for metric {}: {}", metric, amount);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private long getLong(String companyId, String metric) {
        String val = redisTemplate.opsForValue().get("analytics:" + companyId + ":" + metric);
        if (val == null) return 0L;
        try { return Long.parseLong(val.split("\\.")[0]); } catch (Exception e) { return 0L; }
    }

    private BigDecimal getDecimal(String companyId, String metric) {
        String val = redisTemplate.opsForValue().get("analytics:" + companyId + ":" + metric);
        if (val == null) return BigDecimal.ZERO;
        try { return new BigDecimal(val).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}
