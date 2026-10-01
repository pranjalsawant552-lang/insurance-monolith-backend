package com.insurance.agent.repository;

import com.insurance.agent.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByAgentCodeAndDeletedFalse(String code);
    Optional<Agent> findByEmailAndDeletedFalse(String email);
    Optional<Agent> findByUserIdAndDeletedFalse(UUID userId);

    boolean existsByAgentCode(String code);
    boolean existsByEmailAndDeletedFalse(String email);
    boolean existsByLicenseNumberAndDeletedFalse(String licenseNumber);

    Page<Agent> findByCompanyIdAndDeletedFalse(UUID companyId, Pageable pageable);
    Page<Agent> findByCompanyIdAndStatusAndDeletedFalse(UUID companyId, AgentStatus status, Pageable pageable);
    Page<Agent> findByManagerIdAndDeletedFalse(UUID managerId, Pageable pageable);

    @Query("SELECT a FROM Agent a WHERE a.companyId = :cid AND a.deleted = false AND " +
           "(LOWER(a.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           " LOWER(a.lastName)  LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           " LOWER(a.email)     LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           " a.mobileNumber LIKE CONCAT('%',:q,'%') OR " +
           " a.agentCode    LIKE CONCAT('%',:q,'%'))")
    Page<Agent> search(@Param("cid") UUID companyId, @Param("q") String query, Pageable pageable);

    long countByCompanyIdAndStatusAndDeletedFalse(UUID companyId, AgentStatus status);
}
