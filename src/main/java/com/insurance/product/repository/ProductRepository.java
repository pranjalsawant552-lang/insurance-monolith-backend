package com.insurance.product.repository;

import com.insurance.product.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<InsuranceProduct, UUID> {

    Optional<InsuranceProduct> findByCodeAndDeletedFalse(String code);

    Page<InsuranceProduct> findByInsuranceTypeAndActiveAndDeletedFalse(
            InsuranceType type, boolean active, Pageable pageable);

    Page<InsuranceProduct> findByCompanyIdAndDeletedFalse(UUID companyId, Pageable pageable);

    Page<InsuranceProduct> findByCompanyIdAndInsuranceTypeAndDeletedFalse(
            UUID companyId, InsuranceType type, Pageable pageable);

    Page<InsuranceProduct> findByCompanyIdAndActiveAndDeletedFalse(
            UUID companyId, boolean active, Pageable pageable);

    Page<InsuranceProduct> findByFeaturedAndActiveAndDeletedFalse(
            boolean featured, boolean active, Pageable pageable);

    boolean existsByCodeAndDeletedFalse(String code);
}
