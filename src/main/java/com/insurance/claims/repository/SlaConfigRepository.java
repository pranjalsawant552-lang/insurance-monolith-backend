package com.insurance.claims.repository;

import com.insurance.claims.entity.ClaimPriority;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.entity.SlaConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaConfigRepository extends JpaRepository<SlaConfig, UUID> {
    Optional<SlaConfig> findByStatusAndPriority(ClaimStatus status, ClaimPriority priority);
    List<SlaConfig> findAllByOrderByStatusAscPriorityAsc();
}
