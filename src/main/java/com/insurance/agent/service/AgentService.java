package com.insurance.agent.service;

import com.insurance.agent.dto.request.CreateAgentRequest;
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
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final CommissionService commissionService;
    private final com.insurance.agent.event.AuditEventPublisher auditEventPublisher;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public AgentResponse create(CreateAgentRequest req, UUID companyId,
                                UUID branchId, UUID createdBy) {
        if (agentRepository.existsByEmailAndDeletedFalse(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An agent with this email already exists.");
        }
        if (req.getLicenseNumber() != null &&
                agentRepository.existsByLicenseNumberAndDeletedFalse(req.getLicenseNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An agent with this license number already exists.");
        }

        String agentCode = generateAgentCode(req.getAgentType());

        Agent agent = Agent.builder()
                .agentCode(agentCode)
                .agentType(req.getAgentType())
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .email(req.getEmail().toLowerCase().trim())
                .mobileNumber(req.getMobileNumber().trim())
                .panNumber(req.getPanNumber())
                .licenseNumber(req.getLicenseNumber())
                .licenseExpiry(req.getLicenseExpiry())
                .qualification(req.getQualification())
                .region(req.getRegion())
                .companyId(companyId)
                .branchId(branchId)
                .commissionRateLife(req.getCommissionRateLife() != null
                        ? req.getCommissionRateLife() : new BigDecimal("30.00"))
                .commissionRateHealth(req.getCommissionRateHealth() != null
                        ? req.getCommissionRateHealth() : new BigDecimal("15.00"))
                .commissionRateMotor(req.getCommissionRateMotor() != null
                        ? req.getCommissionRateMotor() : new BigDecimal("10.00"))
                .bankName(req.getBankName())
                .accountNumber(req.getAccountNumber())
                .ifscCode(req.getIfscCode())
                .accountHolder(req.getAccountHolder())
                .status(AgentStatus.ACTIVE)
                .createdBy(createdBy)
                .build();

        Agent saved = agentRepository.save(agent);
        auditEventPublisher.publish("AGENT", saved.getId().toString(), "CREATE",
                null,
                Map.of("agentCode", saved.getAgentCode(), "agentType", saved.getAgentType().name(), "status", "ACTIVE"),
                "Agent " + saved.getAgentCode() + " onboarded");
        log.info("Agent created: {} [{}]", saved.getAgentCode(), saved.getId());
        return AgentResponse.from(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public AgentResponse getById(UUID id) {
        return AgentResponse.from(findOrThrow(id));
    }

    public AgentResponse getByCode(String code) {
        return AgentResponse.from(agentRepository.findByAgentCodeAndDeletedFalse(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Agent not found: " + code)));
    }

    // ── List / search ─────────────────────────────────────────────────────────

    public PagedResponse<AgentResponse> list(UUID companyId, AgentStatus status,
                                             int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<Agent> result = status != null
                ? agentRepository.findByCompanyIdAndStatusAndDeletedFalse(companyId, status, pageable)
                : agentRepository.findByCompanyIdAndDeletedFalse(companyId, pageable);
        return PagedResponse.of(result.map(AgentResponse::from));
    }

    public PagedResponse<AgentResponse> search(UUID companyId, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("createdAt").descending());
        return PagedResponse.of(agentRepository.search(companyId, query, pageable)
                .map(AgentResponse::from));
    }

    public PagedResponse<AgentResponse> listTeam(UUID managerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("firstName").ascending());
        return PagedResponse.of(agentRepository.findByManagerIdAndDeletedFalse(managerId, pageable)
                .map(AgentResponse::from));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public AgentResponse update(UUID id, CreateAgentRequest req, UUID updatedBy) {
        Agent agent = findOrThrow(id);
        if (req.getFirstName()       != null) agent.setFirstName(req.getFirstName().trim());
        if (req.getLastName()        != null) agent.setLastName(req.getLastName().trim());
        if (req.getMobileNumber()    != null) agent.setMobileNumber(req.getMobileNumber());
        if (req.getLicenseNumber()   != null) agent.setLicenseNumber(req.getLicenseNumber());
        if (req.getLicenseExpiry()   != null) agent.setLicenseExpiry(req.getLicenseExpiry());
        if (req.getQualification()   != null) agent.setQualification(req.getQualification());
        if (req.getRegion()          != null) agent.setRegion(req.getRegion());
        if (req.getBankName()        != null) agent.setBankName(req.getBankName());
        if (req.getAccountNumber()   != null) agent.setAccountNumber(req.getAccountNumber());
        if (req.getIfscCode()        != null) agent.setIfscCode(req.getIfscCode());
        if (req.getAccountHolder()   != null) agent.setAccountHolder(req.getAccountHolder());
        if (req.getCommissionRateLife()   != null) agent.setCommissionRateLife(req.getCommissionRateLife());
        if (req.getCommissionRateHealth() != null) agent.setCommissionRateHealth(req.getCommissionRateHealth());
        if (req.getCommissionRateMotor()  != null) agent.setCommissionRateMotor(req.getCommissionRateMotor());
        return AgentResponse.from(agentRepository.save(agent));
    }

    // ── Assign manager ────────────────────────────────────────────────────────

    @Transactional
    public AgentResponse assignManager(UUID agentId, UUID managerId, UUID assignedBy) {
        Agent agent   = findOrThrow(agentId);
        Agent manager = findOrThrow(managerId);
        if (agent.getId().equals(manager.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An agent cannot be their own manager.");
        }
        agent.setManager(manager);
        return AgentResponse.from(agentRepository.save(agent));
    }

    // ── Status changes ────────────────────────────────────────────────────────

    @Transactional
    public AgentResponse updateStatus(UUID id, AgentStatus status, UUID updatedBy) {
        Agent agent = findOrThrow(id);
        String previousStatus = agent.getStatus().name();
        agent.setStatus(status);
        Agent saved = agentRepository.save(agent);
        auditEventPublisher.publish("AGENT", saved.getId().toString(), "STATUS_CHANGE",
                Map.of("status", previousStatus),
                Map.of("status", status.name()),
                "Agent " + saved.getAgentCode() + " status changed: " + previousStatus + " → " + status.name());
        return AgentResponse.from(saved);
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id, UUID deletedBy) {
        Agent agent = findOrThrow(id);
        agent.setDeleted(true);
        agent.setDeletedAt(ZonedDateTime.now());
        agent.setDeletedBy(deletedBy);
        agentRepository.save(agent);
        auditEventPublisher.publish("AGENT", agent.getId().toString(), "DELETE",
                Map.of("deleted", "false"),
                Map.of("deleted", "true"),
                "Agent " + agent.getAgentCode() + " soft-deleted");
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getStats(UUID companyId) {
        return Map.of(
                "totalAgents",      agentRepository.countByCompanyIdAndStatusAndDeletedFalse(companyId, AgentStatus.ACTIVE)
                                  + agentRepository.countByCompanyIdAndStatusAndDeletedFalse(companyId, AgentStatus.INACTIVE)
                                  + agentRepository.countByCompanyIdAndStatusAndDeletedFalse(companyId, AgentStatus.SUSPENDED),
                "activeAgents",     agentRepository.countByCompanyIdAndStatusAndDeletedFalse(companyId, AgentStatus.ACTIVE),
                "inactiveAgents",   agentRepository.countByCompanyIdAndStatusAndDeletedFalse(companyId, AgentStatus.INACTIVE),
                "suspendedAgents",  agentRepository.countByCompanyIdAndStatusAndDeletedFalse(companyId, AgentStatus.SUSPENDED)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Agent findOrThrow(UUID id) {
        return agentRepository.findById(id)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Agent not found: " + id));
    }

    private String generateAgentCode(AgentType type) {
        String prefix = switch (type) {
            case AGENT           -> "AGT";
            case BROKER          -> "BRK";
            case CORPORATE_AGENT -> "CRP";
        };
        String code = prefix + "-" + String.valueOf(System.currentTimeMillis()).substring(5);
        while (agentRepository.existsByAgentCode(code)) {
            code = prefix + "-" + System.currentTimeMillis();
        }
        return code;
    }

    private Pageable buildPageable(int page, int size, String sort) {
        String[] parts = (sort != null ? sort : "createdAt,desc").split(",");
        Sort s = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.by(parts[0]).ascending() : Sort.by(parts[0]).descending();
        return PageRequest.of(page, Math.min(size, 100), s);
    }
}
