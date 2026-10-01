package com.insurance.agent.service;

import com.insurance.agent.dto.response.*;
import com.insurance.agent.entity.*;
import com.insurance.agent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final CommissionRepository commissionRepository;
    private final AgentRepository agentRepository;
    private final com.insurance.agent.event.AuditEventPublisher auditEventPublisher;

    // ── Calculate first-year commission on policy issuance ────────────────────

    @Transactional
    public void calculateFirstYearCommission(UUID agentId, UUID policyId, String policyNumber,
                                              UUID customerId, UUID companyId,
                                              String insuranceType, BigDecimal premiumAmount) {
        // Idempotency guard
        if (commissionRepository.existsByPolicyIdAndCommissionType(policyId, "FIRST_YEAR")) {
            log.debug("First-year commission already calculated for policy {}", policyNumber);
            return;
        }

        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null || agent.isDeleted()) {
            log.warn("Agent {} not found for commission calculation", agentId);
            return;
        }

        BigDecimal rate = getRate(agent, insuranceType);
        BigDecimal commissionAmount = premiumAmount
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        AgentCommission commission = AgentCommission.builder()
                .agent(agent)
                .policyId(policyId)
                .policyNumber(policyNumber)
                .customerId(customerId)
                .companyId(companyId)
                .insuranceType(insuranceType)
                .commissionType("FIRST_YEAR")
                .premiumAmount(premiumAmount)
                .commissionRate(rate)
                .commissionAmount(commissionAmount)
                .status(CommissionStatus.PENDING)
                .build();

        commissionRepository.save(commission);
        log.info("Commission calculated for agent {} policy {} amount={}", agentId, policyNumber, commissionAmount);
    }

    // ── Approve a commission ──────────────────────────────────────────────────

    @Transactional
    public CommissionResponse approve(UUID commissionId, UUID approvedBy) {
        AgentCommission c = findOrThrow(commissionId);
        if (c.getStatus() != CommissionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PENDING commissions can be approved.");
        }
        c.setStatus(CommissionStatus.APPROVED);
        AgentCommission saved = commissionRepository.save(c);
        auditEventPublisher.publish("AGENT_COMMISSION", saved.getId().toString(), "APPROVE",
                Map.of("status", "PENDING"),
                Map.of("status", "APPROVED", "amount", saved.getCommissionAmount().toString()),
                "Commission " + saved.getId() + " approved for " + saved.getCommissionAmount());
        return CommissionResponse.from(saved);
    }

    // ── Mark commission as paid ───────────────────────────────────────────────

    @Transactional
    public CommissionResponse markPaid(UUID commissionId, String paymentReference, UUID paidBy) {
        AgentCommission c = findOrThrow(commissionId);
        if (c.getStatus() != CommissionStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only APPROVED commissions can be marked as paid.");
        }
        c.setStatus(CommissionStatus.PAID);
        c.setPaymentReference(paymentReference);
        c.setPaidAt(ZonedDateTime.now());
        AgentCommission saved = commissionRepository.save(c);
        auditEventPublisher.publish("AGENT_COMMISSION", saved.getId().toString(), "MARK_PAID",
                Map.of("status", "APPROVED"),
                Map.of("status", "PAID", "paymentReference", paymentReference != null ? paymentReference : ""),
                "Commission " + saved.getId() + " marked paid, ref " + paymentReference);
        return CommissionResponse.from(saved);
    }

    // ── List commissions for an agent ─────────────────────────────────────────

    public PagedResponse<CommissionResponse> listByAgent(UUID agentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("createdAt").descending());
        return PagedResponse.of(commissionRepository
                .findByAgentIdOrderByCreatedAtDesc(agentId, pageable)
                .map(CommissionResponse::from));
    }

    // ── Agent earnings summary ────────────────────────────────────────────────

    public Map<String, Object> getEarningsSummary(UUID agentId) {
        BigDecimal paid    = commissionRepository.sumPaidByAgent(agentId);
        BigDecimal pending = commissionRepository.sumPendingByAgent(agentId);
        long policies      = commissionRepository.countPoliciesByAgent(agentId);
        return Map.of(
                "totalPaid",       paid    != null ? paid    : BigDecimal.ZERO,
                "totalPending",    pending != null ? pending : BigDecimal.ZERO,
                "totalPolicies",   policies
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AgentCommission findOrThrow(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Commission not found: " + id));
    }

    private BigDecimal getRate(Agent agent, String insuranceType) {
        return switch (insuranceType.toUpperCase()) {
            case "LIFE"   -> agent.getCommissionRateLife();
            case "HEALTH" -> agent.getCommissionRateHealth();
            case "MOTOR"  -> agent.getCommissionRateMotor();
            default       -> new BigDecimal("10.00");
        };
    }
}
