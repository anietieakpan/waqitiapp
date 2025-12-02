package com.waqiti.compliance.consumer;

import com.waqiti.common.events.KYCCompletedEvent;
import com.waqiti.compliance.service.ComplianceService;
import com.waqiti.compliance.service.LimitUpgradeService;
import com.waqiti.compliance.service.RiskAssessmentService;
import com.waqiti.compliance.service.NotificationService;
import com.waqiti.compliance.repository.ProcessedEventRepository;
import com.waqiti.compliance.repository.ComplianceProfileRepository;
import com.waqiti.compliance.model.ProcessedEvent;
import com.waqiti.compliance.model.ComplianceProfile;
import com.waqiti.compliance.model.KYCStatus;
import com.waqiti.compliance.model.VerificationLevel;
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

/**
 * Consumer for KYCCompletedEvent - Critical for compliance workflow completion
 * Updates user limits and enables full platform features after KYC verification
 * ZERO TOLERANCE: All KYC completions must properly update compliance profiles
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KYCCompletedEventConsumer {
    
    private final ComplianceService complianceService;
    private final LimitUpgradeService limitUpgradeService;
    private final RiskAssessmentService riskAssessmentService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ComplianceProfileRepository complianceProfileRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = "compliance.kyc.completed",
        groupId = "compliance-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for compliance updates
    public void handleKYCCompleted(KYCCompletedEvent event) {
        log.info("Processing KYC completion: User {} completed KYC with level {} and score {}", 
            event.getUserId(), event.getVerificationLevel(), event.getComplianceScore());
        
        // IDEMPOTENCY CHECK - Prevent duplicate KYC processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("KYC completion already processed for event: {}", event.getEventId());
            return;
        }
        
        try {
            // Get compliance profile
            ComplianceProfile profile = complianceProfileRepository.findByUserId(event.getUserId())
                .orElseThrow(() -> new RuntimeException("Compliance profile not found for user: " + event.getUserId()));
            
            // STEP 1: Update compliance profile with verification details
            updateComplianceProfile(profile, event);
            
            // STEP 2: Upgrade user transaction limits based on verification level
            upgradeUserLimits(event);
            
            // STEP 3: Update risk assessment with new compliance score
            updateRiskAssessment(event);
            
            // STEP 4: Enable features based on verification level
            enableVerificationLevelFeatures(event);
            
            // STEP 5: Process document retention and archival
            processDocumentRetention(event);
            
            // STEP 6: Update AML monitoring thresholds
            updateAMLMonitoring(event);
            
            // STEP 7: Send verification completion notifications
            sendVerificationNotifications(event);
            
            // STEP 8: Trigger wallet limit update event
            publishWalletLimitUpdateEvent(event);
            
            // STEP 9: Update cross-border capabilities if applicable
            if (event.getVerificationLevel() == VerificationLevel.TIER_3) {
                enableCrossBorderTransactions(event);
            }
            
            // STEP 10: Create compliance certificate
            createComplianceCertificate(event);
            
            // STEP 11: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("KYCCompletedEvent")
                .processedAt(Instant.now())
                .userId(event.getUserId())
                .verificationLevel(event.getVerificationLevel().toString())
                .complianceScore(event.getComplianceScore())
                .limitsUpgraded(true)
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed KYC completion for user: {} - Limits upgraded to {}", 
                event.getUserId(), event.getVerificationLevel());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process KYC completion: {}", 
                event.getUserId(), e);
                
            // Create high-priority manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("KYC completion processing failed", e);
        }
    }
    
    private void updateComplianceProfile(ComplianceProfile profile, KYCCompletedEvent event) {
        // Update profile with KYC verification details
        profile.setKycStatus(KYCStatus.VERIFIED);
        profile.setVerificationLevel(event.getVerificationLevel());
        profile.setComplianceScore(event.getComplianceScore());
        profile.setVerificationDate(event.getVerificationDate());
        profile.setVerificationMethod(event.getVerificationMethod());
        profile.setDocumentsVerified(event.getDocumentsVerified());
        profile.setNextReviewDate(calculateNextReviewDate(event));
        profile.setRiskRating(calculateRiskRating(event.getComplianceScore()));
        profile.setLastUpdated(Instant.now());
        
        // Store verification metadata
        profile.addMetadata("verification_provider", event.getVerificationProvider());
        profile.addMetadata("verification_session_id", event.getSessionId());
        profile.addMetadata("documents_checked", String.join(",", event.getDocumentsVerified()));
        profile.addMetadata("liveness_check_passed", String.valueOf(event.isLivenessCheckPassed()));
        
        complianceProfileRepository.save(profile);
        
        log.info("Compliance profile updated for user: {} with verification level: {}", 
            event.getUserId(), event.getVerificationLevel());
    }
    
    private void upgradeUserLimits(KYCCompletedEvent event) {
        // Upgrade limits based on verification level
        BigDecimal dailyLimit;
        BigDecimal monthlyLimit;
        BigDecimal transactionLimit;
        
        switch (event.getVerificationLevel()) {
            case TIER_1 -> {
                dailyLimit = new BigDecimal("1000");
                monthlyLimit = new BigDecimal("5000");
                transactionLimit = new BigDecimal("500");
            }
            case TIER_2 -> {
                dailyLimit = new BigDecimal("5000");
                monthlyLimit = new BigDecimal("20000");
                transactionLimit = new BigDecimal("2500");
            }
            case TIER_3 -> {
                dailyLimit = new BigDecimal("25000");
                monthlyLimit = new BigDecimal("100000");
                transactionLimit = new BigDecimal("10000");
            }
            case TIER_4 -> {
                dailyLimit = new BigDecimal("100000");
                monthlyLimit = new BigDecimal("500000");
                transactionLimit = new BigDecimal("50000");
            }
            default -> {
                dailyLimit = new BigDecimal("100");
                monthlyLimit = new BigDecimal("500");
                transactionLimit = new BigDecimal("50");
            }
        }
        
        limitUpgradeService.upgradeUserLimits(
            event.getUserId(),
            dailyLimit,
            monthlyLimit,
            transactionLimit,
            event.getVerificationLevel()
        );
        
        log.info("User limits upgraded for {}: Daily: {}, Monthly: {}, Per Transaction: {}", 
            event.getUserId(), dailyLimit, monthlyLimit, transactionLimit);
    }
    
    private void updateRiskAssessment(KYCCompletedEvent event) {
        // Recalculate risk score with KYC verification data
        double riskScore = riskAssessmentService.recalculateRiskScore(
            event.getUserId(),
            event.getComplianceScore(),
            event.getVerificationLevel(),
            event.getDocumentsVerified(),
            event.getCountryCode()
        );
        
        // Update monitoring frequency based on risk
        String monitoringFrequency = riskScore < 30 ? "MONTHLY" : 
                                     riskScore < 60 ? "WEEKLY" : "DAILY";
        
        riskAssessmentService.updateMonitoringFrequency(
            event.getUserId(),
            monitoringFrequency,
            riskScore
        );
        
        log.info("Risk assessment updated for user: {} with score: {} and monitoring: {}", 
            event.getUserId(), riskScore, monitoringFrequency);
    }
    
    private void enableVerificationLevelFeatures(KYCCompletedEvent event) {
        // Enable features based on verification level
        switch (event.getVerificationLevel()) {
            case TIER_1 -> {
                complianceService.enableFeature(event.getUserId(), "BASIC_TRANSFERS");
                complianceService.enableFeature(event.getUserId(), "CARD_PAYMENTS");
            }
            case TIER_2 -> {
                complianceService.enableFeature(event.getUserId(), "BANK_TRANSFERS");
                complianceService.enableFeature(event.getUserId(), "BILL_PAYMENTS");
                complianceService.enableFeature(event.getUserId(), "RECURRING_PAYMENTS");
            }
            case TIER_3 -> {
                complianceService.enableFeature(event.getUserId(), "INTERNATIONAL_TRANSFERS");
                complianceService.enableFeature(event.getUserId(), "CRYPTO_TRANSACTIONS");
                complianceService.enableFeature(event.getUserId(), "INVESTMENT_PRODUCTS");
            }
            case TIER_4 -> {
                complianceService.enableFeature(event.getUserId(), "UNLIMITED_TRANSFERS");
                complianceService.enableFeature(event.getUserId(), "BUSINESS_ACCOUNTS");
                complianceService.enableFeature(event.getUserId(), "API_ACCESS");
                complianceService.enableFeature(event.getUserId(), "WHITE_LABEL_SERVICES");
            }
        }
        
        log.info("Features enabled for user: {} based on verification level: {}", 
            event.getUserId(), event.getVerificationLevel());
    }
    
    private void processDocumentRetention(KYCCompletedEvent event) {
        // Archive KYC documents according to regulatory requirements
        for (String documentId : event.getDocumentIds()) {
            complianceService.archiveDocument(
                documentId,
                event.getUserId(),
                calculateRetentionPeriod(event.getCountryCode()),
                "KYC_VERIFICATION"
            );
        }
        
        // Schedule document expiry review
        complianceService.scheduleDocumentExpiryReview(
            event.getUserId(),
            event.getDocumentIds(),
            calculateDocumentExpiryDate(event)
        );
        
        log.info("KYC documents archived for user: {} with {} documents", 
            event.getUserId(), event.getDocumentIds().size());
    }
    
    private void updateAMLMonitoring(KYCCompletedEvent event) {
        // Update AML monitoring parameters based on verification
        complianceService.updateAMLMonitoring(
            event.getUserId(),
            event.getVerificationLevel(),
            event.getComplianceScore(),
            event.getCountryCode()
        );
        
        // Set transaction monitoring thresholds
        BigDecimal suspiciousThreshold = calculateSuspiciousThreshold(event);
        complianceService.setSuspiciousActivityThreshold(
            event.getUserId(),
            suspiciousThreshold
        );
        
        log.info("AML monitoring updated for user: {} with threshold: {}", 
            event.getUserId(), suspiciousThreshold);
    }
    
    private void sendVerificationNotifications(KYCCompletedEvent event) {
        // Send multi-channel notifications about verification completion
        notificationService.sendKYCCompletionNotification(
            event.getUserId(),
            event.getVerificationLevel(),
            event.getNewDailyLimit(),
            event.getNewMonthlyLimit()
        );
        
        // Send email with verification certificate
        notificationService.sendVerificationCertificateEmail(
            event.getUserId(),
            event.getVerificationLevel(),
            event.getVerificationDate()
        );
        
        log.info("Verification notifications sent for user: {}", event.getUserId());
    }
    
    private void publishWalletLimitUpdateEvent(KYCCompletedEvent event) {
        // Publish event to update wallet limits
        Map<String, Object> limitUpdateEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "userId", event.getUserId(),
            "verificationLevel", event.getVerificationLevel().toString(),
            "newDailyLimit", event.getNewDailyLimit(),
            "newMonthlyLimit", event.getNewMonthlyLimit(),
            "newTransactionLimit", event.getNewTransactionLimit(),
            "effectiveFrom", Instant.now()
        );
        
        kafkaTemplate.send("wallet.limits.updated", limitUpdateEvent);
        
        log.info("Wallet limit update event published for user: {}", event.getUserId());
    }
    
    private void enableCrossBorderTransactions(KYCCompletedEvent event) {
        // Enable cross-border transaction capabilities
        complianceService.enableCrossBorderTransactions(
            event.getUserId(),
            event.getCountryCode(),
            event.getSupportedCountries()
        );
        
        // Set up SWIFT/IBAN capabilities if applicable
        if (event.isSWIFTEnabled()) {
            complianceService.enableSWIFTTransfers(event.getUserId());
        }
        
        // Enable multi-currency wallets
        complianceService.enableMultiCurrencyWallets(
            event.getUserId(),
            event.getSupportedCurrencies()
        );
        
        log.info("Cross-border transactions enabled for user: {} with {} supported countries", 
            event.getUserId(), event.getSupportedCountries().size());
    }
    
    private void createComplianceCertificate(KYCCompletedEvent event) {
        // Generate compliance certificate for user records
        String certificateId = complianceService.generateComplianceCertificate(
            event.getUserId(),
            event.getVerificationLevel(),
            event.getVerificationDate(),
            event.getComplianceScore(),
            calculateCertificateExpiryDate(event)
        );
        
        // Store certificate reference
        complianceService.storeCertificateReference(
            event.getUserId(),
            certificateId,
            event.getVerificationLevel()
        );
        
        log.info("Compliance certificate created for user: {} with ID: {}", 
            event.getUserId(), certificateId);
    }
    
    private LocalDateTime calculateNextReviewDate(KYCCompletedEvent event) {
        // Calculate next review date based on risk and regulations
        return switch (event.getVerificationLevel()) {
            case TIER_1 -> LocalDateTime.now().plusYears(2);
            case TIER_2 -> LocalDateTime.now().plusYears(3);
            case TIER_3 -> LocalDateTime.now().plusYears(5);
            case TIER_4 -> LocalDateTime.now().plusYears(5);
            default -> LocalDateTime.now().plusYears(1);
        };
    }
    
    private String calculateRiskRating(double complianceScore) {
        if (complianceScore >= 90) return "LOW";
        if (complianceScore >= 70) return "MEDIUM";
        if (complianceScore >= 50) return "HIGH";
        return "VERY_HIGH";
    }
    
    private int calculateRetentionPeriod(String countryCode) {
        // Return retention period in years based on country regulations
        return switch (countryCode) {
            case "US" -> 7;
            case "GB" -> 6;
            case "EU" -> 5;
            default -> 7; // Default to longest period
        };
    }
    
    private LocalDateTime calculateDocumentExpiryDate(KYCCompletedEvent event) {
        // Calculate document expiry based on document type and country
        return LocalDateTime.now().plusYears(
            event.getDocumentType().equals("PASSPORT") ? 10 : 5
        );
    }
    
    private BigDecimal calculateSuspiciousThreshold(KYCCompletedEvent event) {
        // Calculate suspicious transaction threshold based on verification level
        return switch (event.getVerificationLevel()) {
            case TIER_1 -> new BigDecimal("500");
            case TIER_2 -> new BigDecimal("2500");
            case TIER_3 -> new BigDecimal("10000");
            case TIER_4 -> new BigDecimal("50000");
            default -> new BigDecimal("100");
        };
    }
    
    private LocalDateTime calculateCertificateExpiryDate(KYCCompletedEvent event) {
        // Certificate validity period based on verification level
        return switch (event.getVerificationLevel()) {
            case TIER_1 -> LocalDateTime.now().plusYears(1);
            case TIER_2 -> LocalDateTime.now().plusYears(2);
            case TIER_3 -> LocalDateTime.now().plusYears(3);
            case TIER_4 -> LocalDateTime.now().plusYears(5);
            default -> LocalDateTime.now().plusMonths(6);
        };
    }
    
    private void createManualInterventionRecord(KYCCompletedEvent event, Exception exception) {
        manualInterventionService.createCriticalTask(
            "KYC_COMPLETION_PROCESSING_FAILED",
            String.format(
                "CRITICAL: Failed to process KYC completion. " +
                "User ID: %s, Verification Level: %s, Compliance Score: %.2f. " +
                "User limits may not be upgraded. Features may not be enabled. " +
                "Exception: %s. IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                event.getUserId(),
                event.getVerificationLevel(),
                event.getComplianceScore(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}