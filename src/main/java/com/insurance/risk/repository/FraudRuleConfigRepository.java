package com.insurance.risk.repository;

import com.insurance.risk.entity.FraudRuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudRuleConfigRepository extends JpaRepository<FraudRuleConfig, UUID> {
    List<FraudRuleConfig> findByActiveTrue();
    Optional<FraudRuleConfig> findByRuleType(FraudRuleConfig.RuleType ruleType);
}
