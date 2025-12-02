package com.waqiti.account.repository;

import com.waqiti.account.model.ClosureDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Closure Document Repository - Production Implementation
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Repository
public interface ClosureDocumentRepository extends JpaRepository<ClosureDocument, UUID> {

    /**
     * Find documents by closure ID
     */
    List<ClosureDocument> findByClosureId(UUID closureId);

    /**
     * Find documents by type
     */
    List<ClosureDocument> findByDocumentType(String documentType);

    /**
     * Find document by closure and type
     */
    ClosureDocument findByClosureIdAndDocumentType(UUID closureId, String documentType);
}
