package com.waqiti.common.security.awareness;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Certificate Generation Service Interface
 *
 * @author Waqiti Platform Team
 */
public interface CertificateGenerationService {

    /**
     * Generate training completion certificate
     */
    String generateTrainingCertificate(UUID employeeId, UUID moduleId, LocalDateTime completionDate);

    /**
     * Generate assessment completion certificate
     */
    String generateAssessmentCertificate(UUID employeeId, UUID assessmentId, LocalDateTime completionDate);
}