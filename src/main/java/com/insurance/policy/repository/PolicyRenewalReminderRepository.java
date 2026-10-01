package com.insurance.policy.repository;

import com.insurance.policy.entity.PolicyRenewalReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PolicyRenewalReminderRepository extends JpaRepository<PolicyRenewalReminder, UUID> {
    boolean existsByPolicyIdAndMilestoneDays(UUID policyId, int milestoneDays);
}
