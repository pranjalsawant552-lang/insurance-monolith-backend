package com.insurance.agent.repository;

import com.insurance.agent.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommissionRepository extends JpaRepository<AgentCommission, UUID> {

    Page<AgentCommission> findByAgentIdOrderByCreatedAtDesc(UUID agentId, Pageable pageable);
    Page<AgentCommission> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, CommissionStatus status, Pageable pageable);
    List<AgentCommission> findByAgentIdAndStatus(UUID agentId, CommissionStatus status);

    boolean existsByPolicyIdAndCommissionType(UUID policyId, String commissionType);

    @Query("SELECT SUM(c.commissionAmount) FROM AgentCommission c WHERE c.agent.id = :aid AND c.status = 'PAID'")
    BigDecimal sumPaidByAgent(@Param("aid") UUID agentId);

    @Query("SELECT SUM(c.commissionAmount) FROM AgentCommission c WHERE c.agent.id = :aid AND c.status = 'PENDING'")
    BigDecimal sumPendingByAgent(@Param("aid") UUID agentId);

    @Query("SELECT COUNT(DISTINCT c.policyId) FROM AgentCommission c WHERE c.agent.id = :aid")
    long countPoliciesByAgent(@Param("aid") UUID agentId);
}
