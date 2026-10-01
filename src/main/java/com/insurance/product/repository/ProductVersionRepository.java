package com.insurance.product.repository;

import com.insurance.product.entity.ProductVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {

    Page<ProductVersion> findByProductIdOrderByVersionNumberDesc(UUID productId, Pageable pageable);

    Optional<ProductVersion> findByProductIdAndVersionNumber(UUID productId, int versionNumber);

    Optional<ProductVersion> findByProductIdAndStatus(UUID productId, ProductVersion.ProductVersionStatus status);

    int countByProductId(UUID productId);
}
