package com.insurance.customer.repository;

import com.insurance.customer.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEmailAndDeletedFalse(String email);
    Optional<Customer> findByMobileNumberAndDeletedFalse(String mobile);
    Optional<Customer> findByCustomerCodeAndDeletedFalse(String code);
    Optional<Customer> findByUserIdAndDeletedFalse(UUID userId);

    boolean existsByEmailAndDeletedFalse(String email);
    boolean existsByMobileNumberAndDeletedFalse(String mobile);
    boolean existsByCustomerCode(String code);

    Page<Customer> findByCompanyIdAndDeletedFalse(UUID companyId, Pageable pageable);
    Page<Customer> findByCompanyIdAndKycStatusAndDeletedFalse(UUID companyId, KycStatus status, Pageable pageable);
    Page<Customer> findByAssignedAgentIdAndDeletedFalse(UUID agentId, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.companyId = :cid AND c.deleted = false AND " +
           "(LOWER(c.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "c.mobileNumber LIKE CONCAT('%',:q,'%') OR " +
           "c.customerCode LIKE CONCAT('%',:q,'%'))")
    Page<Customer> search(@Param("cid") UUID companyId, @Param("q") String query, Pageable pageable);

    long countByCompanyIdAndKycStatusAndDeletedFalse(UUID companyId, KycStatus status);
}
