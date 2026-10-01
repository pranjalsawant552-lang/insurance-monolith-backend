package com.insurance.document.repository;

import com.insurance.document.entity.ExtractionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtractionTemplateRepository extends JpaRepository<ExtractionTemplate, UUID> {
    Optional<ExtractionTemplate> findByDocumentTypeAndIssuerCodeAndActiveTrue(String documentType, String issuerCode);
    List<ExtractionTemplate> findByDocumentTypeOrderByIssuerCodeAsc(String documentType);
}
