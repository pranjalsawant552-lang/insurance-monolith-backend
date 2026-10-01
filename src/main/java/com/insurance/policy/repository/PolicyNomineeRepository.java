package com.insurance.policy.repository;

import com.insurance.policy.entity.PolicyNominee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PolicyNomineeRepository extends JpaRepository<PolicyNominee, UUID> {
    List<PolicyNominee> findByPolicyId(UUID policyId);
}
