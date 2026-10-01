package com.insurance.document.repository;

import com.insurance.document.entity.ExtractionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ExtractionResultRepository extends JpaRepository<ExtractionResult, UUID>,
        JpaSpecificationExecutor<ExtractionResult> {

    Optional<ExtractionResult> findTopByDocumentIdOrderByCreatedAtDesc(UUID documentId);
    Page<ExtractionResult> findByStatus(ExtractionResult.ExtractionStatus status, Pageable pageable);
}
