package com.waqiti.security.service;

import com.waqiti.security.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate service for executing compliance validations to avoid transactional self-invocation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceValidationExecutorService {

    private final ComplianceValidationService complianceValidationService;

    /**
     * Execute comprehensive validation (separate transactional method)
     */
    @Transactional
    public ComplianceValidationResult executeComprehensiveValidation() {
        log.info("Executing comprehensive compliance validation via executor service");
        
        return complianceValidationService.runComprehensiveValidation();
    }

    /**
     * Execute penetration testing (separate transactional method)
     */
    @Transactional
    public PenetrationTestResult executePenetrationTesting() {
        log.info("Executing penetration testing via executor service");
        
        return complianceValidationService.performPenetrationTesting();
    }

    /**
     * Generate compliance report (separate transactional method)
     */
    @Transactional
    public ComplianceReport executeReportGeneration(ComplianceValidationResult validationResult) {
        log.info("Generating compliance report via executor service for validation {}", 
                validationResult.getValidationId());
        
        return complianceValidationService.generateComplianceReport(validationResult);
    }
}