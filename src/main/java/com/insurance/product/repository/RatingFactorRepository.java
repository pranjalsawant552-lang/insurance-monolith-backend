package com.insurance.product.repository;

import com.insurance.product.entity.RatingFactor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatingFactorRepository extends JpaRepository<RatingFactor, UUID> {

    List<RatingFactor> findByProductVersionIdAndActiveTrue(UUID productVersionId);

    List<RatingFactor> findByProductVersionId(UUID productVersionId);

    void deleteByProductVersionId(UUID productVersionId);
}
