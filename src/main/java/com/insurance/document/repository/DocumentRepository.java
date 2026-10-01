package com.insurance.document.repository;

import com.insurance.document.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByDocumentNumberAndDeletedFalse(String docNumber);

    Page<Document> findByOwnerIdAndOwnerTypeAndDeletedFalse(
            UUID ownerId, String ownerType, Pageable pageable);

    Page<Document> findByCompanyIdAndDeletedFalse(UUID companyId, Pageable pageable);

    List<Document> findByOcrStatusAndDeletedFalse(OcrStatus status);

    boolean existsByDocumentNumber(String docNumber);

    @Query("SELECT d FROM Document d WHERE d.ownerId = :oid AND d.ownerType = :otype " +
           "AND d.documentType = :dtype AND d.deleted = false")
    List<Document> findByOwnerAndType(@Param("oid") UUID ownerId,
                                      @Param("otype") String ownerType,
                                      @Param("dtype") String documentType);

    long countByOwnerIdAndOwnerTypeAndDeletedFalse(UUID ownerId, String ownerType);
}
