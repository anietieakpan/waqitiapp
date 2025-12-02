package com.waqiti.security.consumer;

import com.waqiti.common.events.HighRiskTransactionEvent;
import com.waqiti.security.service.RiskAssessmentService;
import com.waqiti.security.service.ComplianceMonitoringService;
import com.waqiti.security.service.TransactionBlockingService;
import com.waqiti.security.service.NotificationService;
import com.waqiti.security.repository.ProcessedEventRepository;
import com.waqiti.security.repository.RiskCaseRepository;
import com.waqiti.security.model.ProcessedEvent;
import com.waqiti.security.model.RiskCase;
import com.waqiti.security.model.RiskLevel;
import com.waqiti.security.model.MonitoringAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for HighRiskTransactionEvent - Critical for AML/BSA compliance
 * Handles enhanced monitoring, compliance reporting, and risk mitigation
 * ZERO TOLERANCE: All high-risk transactions must trigger proper compliance workflows
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HighRiskTransactionEventConsumer {
    
    private final RiskAssessmentService riskAssessmentService;
    private final ComplianceMonitoringService complianceMonitoringService;
    private final TransactionBlockingService transactionBlockingService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final RiskCaseRepository riskCaseRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000");
    
    @KafkaListener(
        topics = "security.transaction.high-risk",
        groupId = "security-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for compliance
    public void handleHighRiskTransaction(HighRiskTransactionEvent event) {
        log.warn("High-risk transaction detected: Transaction {} - User {} - Amount: ${} - Risk Score: {}", 
            event.getTransactionId(), event.getUserId(), event.getAmount(), event.getRiskScore());
        
        // IDEMPOTENCY CHECK - Prevent duplicate risk processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("High-risk transaction already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // STEP 1: Create risk case for investigation
            RiskCase riskCase = createRiskCase(event);
            
            // STEP 2: Apply immediate risk controls
            applyRiskControls(riskCase, event);
            
            // STEP 3: Perform enhanced due diligence
            performEnhancedDueDiligence(riskCase, event);
            
            // STEP 4: Check regulatory reporting requirements
            checkRegulatoryReporting(riskCase, event);
            
            // STEP 5: Initiate real-time monitoring
            initiateEnhancedMonitoring(riskCase, event);
            
            // STEP 6: Analyze transaction patterns
            analyzeTransactionPatterns(riskCase, event);
            
            // STEP 7: Check sanctions and PEP lists
            performSanctionsScreening(riskCase, event);
            
            // STEP 8: Assess geographic risk factors
            assessGeographicRisk(riskCase, event);
            
            // STEP 9: Send compliance alerts
            sendComplianceAlerts(riskCase, event);
            
            // STEP 10: Update user risk profile
            updateUserRiskProfile(riskCase, event);
            
            // STEP 11: Create audit trail for examiners
            createRegulatoryAuditTrail(riskCase, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("HighRiskTransactionEvent")
                .processedAt(Instant.now())
                .riskCaseId(riskCase.getId())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .riskScore(event.getRiskScore())
                .complianceActionsT aken(riskCase.getComplianceActions())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed high-risk transaction: {} - Risk case: {}", 
                event.getTransactionId(), riskCase.getId());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process high-risk transaction: {}", 
                event.getTransactionId(), e);
                
            // Apply emergency compliance controls
            applyEmergencyComplianceControls(event, e);
            
            throw new RuntimeException("High-risk transaction processing failed", e);
        }
    }
    
    private RiskCase createRiskCase(HighRiskTransactionEvent event) {
        RiskCase riskCase = RiskCase.builder()
            .id(UUID.randomUUID().toString())
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .amount(event.getAmount())
            .riskScore(event.getRiskScore())
            .riskLevel(determineRiskLevel(event.getRiskScore()))
            .riskFactors(event.getRiskFactors())
            .currency(event.getCurrency())
            .counterpartyId(event.getCounterpartyId())
            .counterpartyCountry(event.getCounterpartyCountry())
            .transactionType(event.getTransactionType())
            .paymentMethod(event.getPaymentMethod())
            .status("OPEN")
            .createdAt(Instant.now())
            .complianceActions(new ArrayList<>())
            .build();
        
        riskCaseRepository.save(riskCase);
        
        log.info("Risk case created: {} with level: {} for transaction: {}", 
            riskCase.getId(), riskCase.getRiskLevel(), event.getTransactionId());
        
        return riskCase;
    }
    
    private void applyRiskControls(RiskCase riskCase, HighRiskTransactionEvent event) {
        List<String> controlsApplied = new ArrayList<>();
        
        switch (riskCase.getRiskLevel()) {
            case CRITICAL -> {
                // CRITICAL: Hold transaction for manual review
                transactionBlockingService.holdTransaction(
                    event.getTransactionId(),
                    "CRITICAL_RISK_DETECTED",
                    riskCase.getId()
                );
                controlsApplied.add("TRANSACTION_HELD");
                
                // Block all future transactions from user
                transactionBlockingService.blockUserTransactions(
                    event.getUserId(),
                    "HIGH_RISK_USER",
                    LocalDateTime.now().plusHours(24)
                );
                controlsApplied.add("USER_BLOCKED");
                
                // Require enhanced authentication
                riskAssessmentService.requireEnhancedAuth(event.getUserId());
                controlsApplied.add("ENHANCED_AUTH_REQUIRED");
            }
            case HIGH -> {
                // HIGH: Apply velocity limits
                transactionBlockingService.applyVelocityLimits(
                    event.getUserId(),
                    event.getAmount().multiply(new BigDecimal("0.5")), // Reduce by 50%
                    "HIGH_RISK_VELOCITY_LIMIT"
                );
                controlsApplied.add("VELOCITY_LIMITS_APPLIED");
                
                // Require additional verification
                riskAssessmentService.requireAdditionalVerification(
                    event.getTransactionId(),
                    event.getUserId()
                );
                controlsApplied.add("ADDITIONAL_VERIFICATION_REQUIRED");
            }
            case MEDIUM -> {
                // MEDIUM: Enhanced monitoring only
                riskAssessmentService.enableEnhancedMonitoring(
                    event.getUserId(),
                    riskCase.getId()
                );
                controlsApplied.add("ENHANCED_MONITORING_ENABLED");
            }
        }
        
        riskCase.addComplianceActions(controlsApplied);
        riskCaseRepository.save(riskCase);
        
        log.info("Risk controls applied for case {}: {}", riskCase.getId(), controlsApplied);
    }
    
    private void performEnhancedDueDiligence(RiskCase riskCase, HighRiskTransactionEvent event) {
        // Collect additional customer information
        Map<String, Object> eddData = complianceMonitoringService.performEDD(
            event.getUserId(),
            event.getTransactionId(),
            event.getAmount(),
            event.getCounterpartyCountry()
        );
        
        riskCase.setEddData(eddData);
        
        // Verify source of funds
        String sourceVerification = complianceMonitoringService.verifySourceOfFunds(
            event.getUserId(),
            event.getAmount(),
            event.getTransactionType()
        );
        
        riskCase.setSourceOfFundsVerification(sourceVerification);
        
        // Check beneficial ownership
        if (event.getAmount().compareTo(new BigDecimal("3000")) > 0) {
            Map<String, Object> beneficialOwnership = complianceMonitoringService
                .checkBeneficialOwnership(event.getUserId());
            riskCase.setBeneficialOwnershipData(beneficialOwnership);
        }
        
        riskCaseRepository.save(riskCase);
        
        log.info("Enhanced due diligence completed for case: {}", riskCase.getId());
    }
    
    private void checkRegulatoryReporting(RiskCase riskCase, HighRiskTransactionEvent event) {
        List<String> reportsGenerated = new ArrayList<>();
        
        // Check Currency Transaction Report (CTR) requirement
        if (event.getAmount().compareTo(CTR_THRESHOLD) > 0) {
            String ctrId = complianceMonitoringService.generateCTR(
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getCurrency(),
                event.getTransactionType()
            );
            
            riskCase.setCtrId(ctrId);
            reportsGenerated.add("CTR_FILED");
            
            log.info("CTR generated for transaction {} - CTR ID: {}", 
                event.getTransactionId(), ctrId);
        }
        
        // Check Suspicious Activity Report (SAR) requirement
        if (shouldFileSAR(event, riskCase)) {
            String sarId = complianceMonitoringService.generateSAR(
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount(),
                riskCase.getRiskFactors(),
                "High-risk transaction patterns detected"
            );
            
            riskCase.setSarId(sarId);
            reportsGenerated.add("SAR_FILED");
            
            log.warn("SAR generated for high-risk transaction {} - SAR ID: {}", 
                event.getTransactionId(), sarId);
        }
        
        // International reporting for cross-border transactions
        if (event.getCounterpartyCountry() != null && 
            !event.getCounterpartyCountry().equals("US")) {
            
            String fbarId = complianceMonitoringService.checkFBARReporting(
                event.getUserId(),
                event.getAmount(),
                event.getCounterpartyCountry()
            );
            
            if (fbarId != null) {
                riskCase.setFbarId(fbarId);
                reportsGenerated.add("FBAR_REQUIREMENT_NOTED");
            }
        }
        
        riskCase.addComplianceActions(reportsGenerated);
        riskCaseRepository.save(riskCase);
        
        log.info("Regulatory reporting completed for case {}: {}", 
            riskCase.getId(), reportsGenerated);
    }
    
    private void initiateEnhancedMonitoring(RiskCase riskCase, HighRiskTransactionEvent event) {
        // Set up real-time monitoring for user
        String monitoringId = complianceMonitoringService.initiateEnhancedMonitoring(
            event.getUserId(),
            riskCase.getRiskLevel(),
            calculateMonitoringDuration(riskCase.getRiskLevel()),
            riskCase.getId()
        );
        
        riskCase.setMonitoringId(monitoringId);
        
        // Configure monitoring parameters based on risk factors
        Map<String, Object> monitoringParams = Map.of(
            "velocityThreshold", calculateVelocityThreshold(event.getAmount()),
            "geographicRestrictions", getGeographicRestrictions(event.getCounterpartyCountry()),
            "timeBasedLimits", getTimeBasedLimits(riskCase.getRiskLevel()),
            "counterpartyLimits", getCounterpartyLimits(event.getCounterpartyId())
        );
        
        complianceMonitoringService.configureMonitoring(monitoringId, monitoringParams);
        
        riskCaseRepository.save(riskCase);
        
        log.info("Enhanced monitoring initiated for case {}: Monitoring ID: {}", 
            riskCase.getId(), monitoringId);
    }
    
    private void analyzeTransactionPatterns(RiskCase riskCase, HighRiskTransactionEvent event) {
        // Analyze historical patterns for this user
        Map<String, Object> patternAnalysis = riskAssessmentService.analyzeTransactionPatterns(
            event.getUserId(),
            LocalDateTime.now().minusDays(90),
            LocalDateTime.now()
        );
        
        riskCase.setPatternAnalysis(patternAnalysis);
        
        // Check for structuring patterns
        boolean structuring = riskAssessmentService.checkStructuringPatterns(
            event.getUserId(),
            event.getAmount(),
            LocalDateTime.now().minusDays(30)
        );
        
        if (structuring) {
            riskCase.addRiskFactor("POTENTIAL_STRUCTURING");
            riskCase.setStructuringDetected(true);
            
            log.warn("Potential structuring detected for user {} in case {}", 
                event.getUserId(), riskCase.getId());
        }
        
        // Check for rapid movement patterns
        boolean rapidMovement = riskAssessmentService.checkRapidMovementPatterns(
            event.getUserId(),
            event.getAmount(),
            LocalDateTime.now().minusHours(24)
        );
        
        if (rapidMovement) {
            riskCase.addRiskFactor("RAPID_FUND_MOVEMENT");
            riskCase.setRapidMovementDetected(true);
        }
        
        riskCaseRepository.save(riskCase);
        
        log.info("Transaction pattern analysis completed for case: {}", riskCase.getId());
    }
    
    private void performSanctionsScreening(RiskCase riskCase, HighRiskTransactionEvent event) {
        // Screen against OFAC SDN list
        boolean ofacMatch = complianceMonitoringService.screenAgainstOFAC(
            event.getUserId(),
            event.getCounterpartyId(),
            event.getCounterpartyCountry()
        );
        
        if (ofacMatch) {
            riskCase.addRiskFactor("OFAC_MATCH_DETECTED");
            riskCase.setOfacScreeningResult("MATCH");
            
            // Immediately block transaction
            transactionBlockingService.emergencyBlock(
                event.getTransactionId(),
                "OFAC_SANCTIONS_MATCH"
            );
            
            log.error("OFAC match detected for transaction {} - EMERGENCY BLOCK APPLIED", 
                event.getTransactionId());
        }
        
        // Screen against PEP (Politically Exposed Persons) list
        boolean pepMatch = complianceMonitoringService.screenAgainstPEP(
            event.getUserId(),
            event.getCounterpartyId()
        );
        
        if (pepMatch) {
            riskCase.addRiskFactor("PEP_EXPOSURE");
            riskCase.setPepScreeningResult("MATCH");
            riskCase.setRiskLevel(RiskLevel.HIGH); // Escalate risk level
        }
        
        riskCaseRepository.save(riskCase);
        
        log.info("Sanctions screening completed for case: {} - OFAC: {}, PEP: {}", 
            riskCase.getId(), ofacMatch, pepMatch);
    }
    
    private void assessGeographicRisk(RiskCase riskCase, HighRiskTransactionEvent event) {
        if (event.getCounterpartyCountry() != null) {
            // Check country risk rating
            String countryRisk = complianceMonitoringService.getCountryRiskRating(
                event.getCounterpartyCountry()
            );
            
            riskCase.setCountryRiskRating(countryRisk);
            
            // Check if country is high-risk
            if ("HIGH".equals(countryRisk) || "VERY_HIGH".equals(countryRisk)) {
                riskCase.addRiskFactor("HIGH_RISK_COUNTRY");
                
                // Apply additional controls for high-risk countries
                transactionBlockingService.applyCountryRestrictions(
                    event.getUserId(),
                    event.getCounterpartyCountry(),
                    riskCase.getId()
                );
            }
            
            // Check if country has capital controls
            boolean hasCapitalControls = complianceMonitoringService
                .checkCapitalControls(event.getCounterpartyCountry());
            
            if (hasCapitalControls) {
                riskCase.addRiskFactor("CAPITAL_CONTROLS_COUNTRY");
            }
        }
        
        riskCaseRepository.save(riskCase);
        
        log.info("Geographic risk assessment completed for case: {}", riskCase.getId());
    }
    
    private void sendComplianceAlerts(RiskCase riskCase, HighRiskTransactionEvent event) {
        // Send alert to compliance team
        notificationService.sendComplianceAlert(
            riskCase.getId(),
            event.getTransactionId(),
            event.getUserId(),
            riskCase.getRiskLevel(),
            riskCase.getRiskFactors(),
            event.getAmount()
        );
        
        // Send alert to AML officer if SAR filed
        if (riskCase.getSarId() != null) {
            notificationService.sendAMLOfficerAlert(
                riskCase.getId(),
                riskCase.getSarId(),
                event.getTransactionId(),
                "SAR filed for high-risk transaction"
            );
        }
        
        // Send regulatory alert if required
        if (riskCase.getRiskLevel() == RiskLevel.CRITICAL) {
            notificationService.sendRegulatoryAlert(
                riskCase.getId(),
                event.getTransactionId(),
                "Critical risk transaction requiring immediate attention"
            );
        }
        
        log.info("Compliance alerts sent for case: {}", riskCase.getId());
    }
    
    private void updateUserRiskProfile(RiskCase riskCase, HighRiskTransactionEvent event) {
        // Update user's overall risk score
        double newRiskScore = riskAssessmentService.updateUserRiskScore(
            event.getUserId(),
            event.getRiskScore(),
            riskCase.getRiskFactors()
        );
        
        // Update risk classification
        String riskClassification = determineRiskClassification(newRiskScore);
        riskAssessmentService.updateUserRiskClassification(
            event.getUserId(),
            riskClassification
        );
        
        // Update monitoring requirements
        String monitoringLevel = determineMonitoringLevel(riskClassification);
        complianceMonitoringService.updateMonitoringLevel(
            event.getUserId(),
            monitoringLevel
        );
        
        log.info("User {} risk profile updated - Score: {}, Classification: {}", 
            event.getUserId(), newRiskScore, riskClassification);
    }
    
    private void createRegulatoryAuditTrail(RiskCase riskCase, HighRiskTransactionEvent event) {
        // Create comprehensive audit record for regulatory examinations
        Map<String, Object> auditRecord = Map.of(
            "caseId", riskCase.getId(),
            "transactionId", event.getTransactionId(),
            "userId", event.getUserId(),
            "riskScore", event.getRiskScore(),
            "riskFactors", riskCase.getRiskFactors(),
            "controlsApplied", riskCase.getComplianceActions(),
            "reportsGenerated", Arrays.asList(riskCase.getCtrId(), riskCase.getSarId()),
            "processedAt", Instant.now(),
            "processedBy", "AUTOMATED_SYSTEM"
        );
        
        complianceMonitoringService.createAuditRecord(
            "HIGH_RISK_TRANSACTION_PROCESSING",
            auditRecord,
            "BSA_AML_COMPLIANCE"
        );
        
        log.info("Regulatory audit trail created for case: {}", riskCase.getId());
    }
    
    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 90) return RiskLevel.CRITICAL;
        if (riskScore >= 70) return RiskLevel.HIGH;
        if (riskScore >= 50) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
    
    private boolean shouldFileSAR(HighRiskTransactionEvent event, RiskCase riskCase) {
        return event.getAmount().compareTo(SAR_THRESHOLD) > 0 ||
               riskCase.getRiskScore() >= 85 ||
               riskCase.getRiskFactors().contains("OFAC_MATCH_DETECTED") ||
               riskCase.getRiskFactors().contains("POTENTIAL_STRUCTURING") ||
               riskCase.getRiskFactors().contains("PEP_EXPOSURE");
    }
    
    private int calculateMonitoringDuration(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case CRITICAL -> 90; // 90 days
            case HIGH -> 60;     // 60 days
            case MEDIUM -> 30;   // 30 days
            case LOW -> 7;       // 7 days
        };
    }
    
    private BigDecimal calculateVelocityThreshold(BigDecimal transactionAmount) {
        // Set velocity threshold as 3x the current transaction amount
        return transactionAmount.multiply(new BigDecimal("3"));
    }
    
    private List<String> getGeographicRestrictions(String counterpartyCountry) {
        if (counterpartyCountry == null) return Collections.emptyList();
        
        // High-risk countries with restrictions
        Set<String> highRiskCountries = Set.of("AF", "BY", "MM", "KP", "IR", "SY");
        
        if (highRiskCountries.contains(counterpartyCountry)) {
            return List.of("BLOCKED_COUNTRY", "ENHANCED_VERIFICATION_REQUIRED");
        }
        
        return Collections.emptyList();
    }
    
    private Map<String, Object> getTimeBasedLimits(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case CRITICAL -> Map.of("daily", 0, "weekly", 0, "monthly", 0);
            case HIGH -> Map.of("daily", 1000, "weekly", 5000, "monthly", 15000);
            case MEDIUM -> Map.of("daily", 5000, "weekly", 25000, "monthly", 75000);
            case LOW -> Map.of("daily", 10000, "weekly", 50000, "monthly", 150000);
        };
    }
    
    private Map<String, Object> getCounterpartyLimits(String counterpartyId) {
        if (counterpartyId == null) return Collections.emptyMap();
        
        return Map.of(
            "maxSingleTransaction", 10000,
            "maxDailyVolume", 25000,
            "maxMonthlyVolume", 100000
        );
    }
    
    private String determineRiskClassification(double riskScore) {
        if (riskScore >= 80) return "HIGH_RISK";
        if (riskScore >= 60) return "MEDIUM_RISK";
        if (riskScore >= 40) return "ELEVATED_RISK";
        return "STANDARD_RISK";
    }
    
    private String determineMonitoringLevel(String riskClassification) {
        return switch (riskClassification) {
            case "HIGH_RISK" -> "INTENSIVE";
            case "MEDIUM_RISK" -> "ENHANCED";
            case "ELEVATED_RISK" -> "HEIGHTENED";
            default -> "STANDARD";
        };
    }
    
    private void applyEmergencyComplianceControls(HighRiskTransactionEvent event, Exception originalException) {
        try {
            log.error("EMERGENCY: Applying emergency compliance controls for transaction: {}", 
                event.getTransactionId());
            
            // Emergency transaction hold
            transactionBlockingService.emergencyHold(
                event.getTransactionId(),
                "COMPLIANCE_PROCESSING_FAILURE"
            );
            
            // Emergency user restrictions
            transactionBlockingService.emergencyUserBlock(
                event.getUserId(),
                "HIGH_RISK_PROCESSING_FAILURE"
            );
            
            // Emergency compliance notification
            notificationService.sendEmergencyComplianceAlert(
                event.getTransactionId(),
                event.getUserId(),
                event.getAmount(),
                "High-risk transaction processing failure - immediate manual review required"
            );
            
        } catch (Exception e) {
            log.error("Failed to apply emergency compliance controls", e);
        }
    }
}