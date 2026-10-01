package com.insurance.risk.repository;

import com.insurance.risk.entity.RiskFactorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskFactorConfigRepository extends JpaRepository<RiskFactorConfig, UUID> {
    List<RiskFactorConfig> findByInsuranceTypeAndActiveTrue(String insuranceType);
    List<RiskFactorConfig> findByInsuranceType(String insuranceType);
}
