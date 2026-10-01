package com.insurance.document.service;

import com.insurance.document.dto.request.ExtractionTemplateRequest;
import com.insurance.document.entity.ExtractionTemplate;
import com.insurance.document.repository.ExtractionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for {@link ExtractionTemplate} — this is what lets an ops team
 * add support for a new insurer's document layout, or fix a template that's
 * matching poorly, without any code change or redeploy. Every template
 * change is audited (who changed what pattern, when) since it directly
 * affects what gets extracted from real customer documents going forward.
 */
@Service
@RequiredArgsConstructor
public class ExtractionTemplateService {

    private final ExtractionTemplateRepository repository;
    private final com.insurance.document.event.AuditEventPublisher auditEventPublisher;

    public List<ExtractionTemplate> listForDocumentType(String documentType) {
        return repository.findByDocumentTypeOrderByIssuerCodeAsc(documentType.toUpperCase());
    }

    @Transactional
    public ExtractionTemplate create(ExtractionTemplateRequest req, UUID createdBy) {
        ExtractionTemplate template = ExtractionTemplate.builder()
                .documentType(req.getDocumentType().toUpperCase())
                .issuerCode(req.getIssuerCode().toUpperCase())
                .issuerName(req.getIssuerName())
                .fieldRules(req.getFieldRules())
                .active(req.isActive())
                .createdBy(createdBy)
                .build();
        ExtractionTemplate saved = repository.save(template);

        auditEventPublisher.publish("EXTRACTION_TEMPLATE", saved.getId().toString(), "CREATE",
                null,
                java.util.Map.of("documentType", saved.getDocumentType(), "issuerCode", saved.getIssuerCode(),
                        "fieldCount", String.valueOf(saved.getFieldRules().size())),
                "Extraction template created: " + saved.getDocumentType() + " / " + saved.getIssuerCode());

        return saved;
    }

    @Transactional
    public ExtractionTemplate update(UUID id, ExtractionTemplateRequest req, UUID updatedBy) {
        ExtractionTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extraction template not found: " + id));

        template.setIssuerName(req.getIssuerName());
        template.setFieldRules(req.getFieldRules());
        template.setActive(req.isActive());
        template.setUpdatedAt(ZonedDateTime.now());
        ExtractionTemplate saved = repository.save(template);

        auditEventPublisher.publish("EXTRACTION_TEMPLATE", saved.getId().toString(), "UPDATE",
                null,
                java.util.Map.of("documentType", saved.getDocumentType(), "issuerCode", saved.getIssuerCode(),
                        "fieldCount", String.valueOf(saved.getFieldRules().size()), "active", String.valueOf(saved.isActive())),
                "Extraction template updated: " + saved.getDocumentType() + " / " + saved.getIssuerCode());

        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        ExtractionTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extraction template not found: " + id));
        repository.delete(template);
        auditEventPublisher.publish("EXTRACTION_TEMPLATE", id.toString(), "DELETE",
                java.util.Map.of("documentType", template.getDocumentType(), "issuerCode", template.getIssuerCode()),
                null,
                "Extraction template deleted: " + template.getDocumentType() + " / " + template.getIssuerCode());
    }
}
