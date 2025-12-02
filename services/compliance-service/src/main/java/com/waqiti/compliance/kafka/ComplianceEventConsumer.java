package com.waqiti.compliance.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.compliance.model.*;
import com.waqiti.compliance.repository.ComplianceEventRepository;
import com.waqiti.compliance.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for core compliance events
 * Handles AML monitoring, sanctions screening, regulatory reporting, and compliance violations
 * 
 * Critical for: Regulatory compliance, legal requirements, operational licensing
 * SLA: Must process compliance events within 30 seconds to meet regulatory deadlines
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ComplianceEventConsumer {

    private final ComplianceEventRepository complianceEventRepository;
    private final AMLService amlService;
    private final SanctionsScreeningService sanctionsService;
    private final RegulatoryReportingService regulatoryService;
    private final KYCService kycService;
    private final ComplianceAlertService alertService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;

    private static final BigDecimal AML_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 30000; // 30 seconds
    
    @KafkaListener(
        topics = "compliance-events",
        groupId = "compliance-event-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "compliance-event-processor", fallbackMethod = "handleComplianceEventFailure")
    @Retry(name = "compliance-event-processor")
    public void processComplianceEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing compliance event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            ComplianceEvent complianceEvent = extractComplianceEvent(payload);
            
            // Validate compliance event
            validateComplianceEvent(complianceEvent);
            
            // Check for duplicate processing
            if (isDuplicateEvent(complianceEvent)) {
                log.warn("Duplicate compliance event detected: {}, skipping", complianceEvent.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Classify event type and urgency
            EventClassification classification = classifyEvent(complianceEvent);
            
            // Process based on event type
            ComplianceProcessingResult result = processEventByType(complianceEvent, classification);
            
            // Perform AML screening if required
            if (classification.requiresAMLScreening()) {
                performAMLScreening(complianceEvent, result);
            }
            
            // Perform sanctions screening if required
            if (classification.requiresSanctionsScreening()) {
                performSanctionsScreening(complianceEvent, result);
            }
            
            // Generate regulatory reports if required
            if (classification.requiresReporting()) {
                generateRegulatoryReports(complianceEvent, result);
            }
            
            // Handle compliance violations
            if (result.hasViolations()) {
                handleComplianceViolations(complianceEvent, result);
            }
            
            // Update compliance records
            updateComplianceRecords(complianceEvent, result);
            
            // Send notifications
            sendComplianceNotifications(complianceEvent, result);
            
            // Audit compliance processing
            auditComplianceEvent(complianceEvent, result, event);
            
            // Record metrics
            recordComplianceMetrics(complianceEvent, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed compliance event: {} type: {} in {}ms", 
                    complianceEvent.getEventId(), complianceEvent.getEventType(),
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for compliance event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (ComplianceViolationException e) {
            log.error("Compliance violation detected: {}", eventId, e);
            handleComplianceViolation(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process compliance event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private ComplianceEvent extractComplianceEvent(Map<String, Object> payload) {
        return ComplianceEvent.builder()
            .eventId(extractString(payload, "eventId", UUID.randomUUID().toString()))
            .eventType(ComplianceEventType.fromString(extractString(payload, "eventType", null)))
            .entityId(extractString(payload, "entityId", null))
            .entityType(extractString(payload, "entityType", null))
            .customerId(extractString(payload, "customerId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .country(extractString(payload, "country", null))
            .riskLevel(extractString(payload, "riskLevel", "MEDIUM"))
            .description(extractString(payload, "description", null))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .urgency(extractString(payload, "urgency", "NORMAL"))
            .metadata(extractMap(payload, "metadata"))
            .timestamp(Instant.now())
            .build();
    }

    private void validateComplianceEvent(ComplianceEvent event) {
        if (event.getEventType() == null) {
            throw new ValidationException("Event type is required for compliance event");
        }
        
        if (event.getEntityId() == null || event.getEntityId().isEmpty()) {
            throw new ValidationException("Entity ID is required for compliance event");
        }
        
        if (event.getEntityType() == null || event.getEntityType().isEmpty()) {
            throw new ValidationException("Entity type is required for compliance event");
        }
        
        // Validate event type specific requirements
        switch (event.getEventType()) {
            case TRANSACTION_MONITORING:
                if (event.getTransactionId() == null) {
                    throw new ValidationException("Transaction ID required for transaction monitoring event");
                }
                if (event.getAmount() == null) {
                    throw new ValidationException("Amount required for transaction monitoring event");
                }
                break;
                
            case CUSTOMER_SCREENING:
                if (event.getCustomerId() == null) {
                    throw new ValidationException("Customer ID required for customer screening event");
                }
                break;
                
            case MERCHANT_SCREENING:
                if (event.getMerchantId() == null) {
                    throw new ValidationException("Merchant ID required for merchant screening event");
                }
                break;
                
            case KYC_UPDATE:
                if (event.getCustomerId() == null) {
                    throw new ValidationException("Customer ID required for KYC update event");
                }
                break;
                
            case REGULATORY_REPORT:
                if (event.getDescription() == null) {
                    throw new ValidationException("Description required for regulatory report event");
                }
                break;
        }
    }

    private boolean isDuplicateEvent(ComplianceEvent event) {
        return complianceEventRepository.existsByEventIdAndTimestampAfter(
            event.getEventId(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private EventClassification classifyEvent(ComplianceEvent event) {
        EventClassification classification = new EventClassification();
        classification.setEventType(event.getEventType());
        classification.setUrgency(determineUrgency(event));
        classification.setRiskLevel(determineRiskLevel(event));
        
        // Determine required processing steps
        switch (event.getEventType()) {
            case TRANSACTION_MONITORING:
                classification.setRequiresAMLScreening(true);
                classification.setRequiresSanctionsScreening(event.getAmount().compareTo(AML_THRESHOLD) >= 0);
                classification.setRequiresReporting(event.getAmount().compareTo(CTR_THRESHOLD) >= 0);
                break;
                
            case CUSTOMER_SCREENING:
            case MERCHANT_SCREENING:
                classification.setRequiresSanctionsScreening(true);
                classification.setRequiresKYCVerification(true);
                break;
                
            case KYC_UPDATE:
                classification.setRequiresKYCVerification(true);
                classification.setRequiresRiskAssessment(true);
                break;
                
            case SANCTIONS_HIT:
                classification.setUrgency("URGENT");
                classification.setRequiresImmediateAction(true);
                classification.setRequiresReporting(true);
                break;
                
            case AML_ALERT:
                classification.setRequiresInvestigation(true);
                classification.setRequiresReporting(event.getAmount().compareTo(SAR_THRESHOLD) >= 0);
                break;
                
            case REGULATORY_REPORT:
                classification.setRequiresReporting(true);
                classification.setRequiresValidation(true);
                break;
        }
        
        return classification;
    }

    private String determineUrgency(ComplianceEvent event) {
        // High urgency conditions
        if (event.getEventType() == ComplianceEventType.SANCTIONS_HIT ||
            event.getEventType() == ComplianceEventType.FRAUD_ALERT ||
            "HIGH".equals(event.getRiskLevel()) ||
            (event.getAmount() != null && event.getAmount().compareTo(new BigDecimal("100000")) > 0)) {
            return "URGENT";
        }
        
        // Medium urgency
        if (event.getEventType() == ComplianceEventType.AML_ALERT ||
            "MEDIUM".equals(event.getRiskLevel()) ||
            (event.getAmount() != null && event.getAmount().compareTo(AML_THRESHOLD) >= 0)) {
            return "HIGH";
        }
        
        return "NORMAL";
    }

    private String determineRiskLevel(ComplianceEvent event) {
        int riskScore = 0;
        
        // Risk factors
        if (event.getAmount() != null) {
            if (event.getAmount().compareTo(new BigDecimal("100000")) > 0) {
                riskScore += 30;
            } else if (event.getAmount().compareTo(AML_THRESHOLD) > 0) {
                riskScore += 20;
            } else if (event.getAmount().compareTo(new BigDecimal("5000")) > 0) {
                riskScore += 10;
            }
        }
        
        // Country risk
        if (event.getCountry() != null && isHighRiskCountry(event.getCountry())) {
            riskScore += 25;
        }
        
        // Event type risk
        switch (event.getEventType()) {
            case SANCTIONS_HIT:
                riskScore += 50;
                break;
            case AML_ALERT:
                riskScore += 40;
                break;
            case FRAUD_ALERT:
                riskScore += 35;
                break;
            case SUSPICIOUS_ACTIVITY:
                riskScore += 30;
                break;
        }
        
        // Return risk level
        if (riskScore >= 70) {
            return "CRITICAL";
        } else if (riskScore >= 50) {
            return "HIGH";
        } else if (riskScore >= 30) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private boolean isHighRiskCountry(String country) {
        // List of high-risk countries for AML/sanctions
        Set<String> highRiskCountries = Set.of(
            "AF", "BY", "CF", "CD", "CU", "ER", "GW", "HT", "IR", "IQ", "LB", "LY", 
            "ML", "MM", "NI", "KP", "SO", "SS", "SD", "SY", "UA", "VE", "YE", "ZW"
        );
        return highRiskCountries.contains(country);
    }

    private ComplianceProcessingResult processEventByType(ComplianceEvent event, EventClassification classification) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        result.setEventId(event.getEventId());
        result.setProcessingStartTime(Instant.now());
        
        switch (event.getEventType()) {
            case TRANSACTION_MONITORING:
                result = processTransactionMonitoring(event);
                break;
                
            case CUSTOMER_SCREENING:
                result = processCustomerScreening(event);
                break;
                
            case MERCHANT_SCREENING:
                result = processMerchantScreening(event);
                break;
                
            case KYC_UPDATE:
                result = processKYCUpdate(event);
                break;
                
            case SANCTIONS_HIT:
                result = processSanctionsHit(event);
                break;
                
            case AML_ALERT:
                result = processAMLAlert(event);
                break;
                
            case FRAUD_ALERT:
                result = processFraudAlert(event);
                break;
                
            case SUSPICIOUS_ACTIVITY:
                result = processSuspiciousActivity(event);
                break;
                
            case REGULATORY_REPORT:
                result = processRegulatoryReport(event);
                break;
                
            default:
                log.warn("Unknown compliance event type: {}", event.getEventType());
                result.setStatus(ProcessingStatus.UNSUPPORTED);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private ComplianceProcessingResult processTransactionMonitoring(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Analyze transaction patterns
        TransactionAnalysis analysis = amlService.analyzeTransaction(
            event.getTransactionId(),
            event.getAmount(),
            event.getCurrency(),
            event.getCustomerId()
        );
        
        result.setAnalysisResult(analysis);
        
        // Check for suspicious patterns
        if (analysis.hasSuspiciousPatterns()) {
            result.addViolation(new ComplianceViolation(
                "SUSPICIOUS_TRANSACTION_PATTERN",
                "Transaction exhibits suspicious patterns",
                "MEDIUM"
            ));
        }
        
        // Check velocity limits
        if (analysis.exceedsVelocityLimits()) {
            result.addViolation(new ComplianceViolation(
                "VELOCITY_LIMIT_EXCEEDED",
                "Transaction velocity limits exceeded",
                "HIGH"
            ));
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private ComplianceProcessingResult processCustomerScreening(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Perform comprehensive customer screening
        CustomerScreeningResult screening = sanctionsService.screenCustomer(
            event.getCustomerId(),
            event.getMetadata()
        );
        
        result.setScreeningResult(screening);
        
        if (screening.hasMatches()) {
            for (SanctionsMatch match : screening.getMatches()) {
                result.addViolation(new ComplianceViolation(
                    "SANCTIONS_MATCH",
                    "Customer matches sanctions list: " + match.getListName(),
                    "CRITICAL"
                ));
            }
        }
        
        // Check PEP status
        if (screening.isPEP()) {
            result.addViolation(new ComplianceViolation(
                "PEP_DETECTED",
                "Customer identified as Politically Exposed Person",
                "HIGH"
            ));
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private ComplianceProcessingResult processMerchantScreening(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Screen merchant against sanctions lists
        MerchantScreeningResult screening = sanctionsService.screenMerchant(
            event.getMerchantId(),
            event.getMetadata()
        );
        
        result.setScreeningResult(screening);
        
        if (screening.hasMatches()) {
            for (SanctionsMatch match : screening.getMatches()) {
                result.addViolation(new ComplianceViolation(
                    "MERCHANT_SANCTIONS_MATCH",
                    "Merchant matches sanctions list: " + match.getListName(),
                    "CRITICAL"
                ));
            }
        }
        
        // Check business risk factors
        BusinessRiskAssessment riskAssessment = amlService.assessMerchantRisk(event.getMerchantId());
        if (riskAssessment.isHighRisk()) {
            result.addViolation(new ComplianceViolation(
                "HIGH_RISK_MERCHANT",
                "Merchant classified as high risk: " + riskAssessment.getRiskFactors(),
                "HIGH"
            ));
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private ComplianceProcessingResult processKYCUpdate(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Update KYC information
        KYCUpdateResult kycResult = kycService.updateCustomerKYC(
            event.getCustomerId(),
            event.getMetadata()
        );
        
        result.setKycResult(kycResult);
        
        // Validate KYC completeness
        if (!kycResult.isComplete()) {
            result.addViolation(new ComplianceViolation(
                "INCOMPLETE_KYC",
                "KYC documentation is incomplete: " + kycResult.getMissingDocuments(),
                "MEDIUM"
            ));
        }
        
        // Check for enhanced due diligence requirements
        if (kycResult.requiresEnhancedDueDiligence()) {
            result.addViolation(new ComplianceViolation(
                "EDD_REQUIRED",
                "Enhanced Due Diligence required for customer",
                "HIGH"
            ));
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private ComplianceProcessingResult processSanctionsHit(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Immediate escalation for sanctions hit
        result.addViolation(new ComplianceViolation(
            "SANCTIONS_HIT_CONFIRMED",
            "Confirmed sanctions match detected",
            "CRITICAL"
        ));
        
        // Freeze associated accounts/transactions
        if (event.getCustomerId() != null) {
            accountService.freezeCustomerAccounts(event.getCustomerId(), "SANCTIONS_HIT");
        }
        
        if (event.getTransactionId() != null) {
            transactionService.blockTransaction(event.getTransactionId(), "SANCTIONS_HIT");
        }
        
        // Create immediate alert
        alertService.createUrgentAlert(
            "SANCTIONS_HIT",
            event.getEntityId(),
            "Immediate action required - sanctions match detected"
        );
        
        result.setStatus(ProcessingStatus.BLOCKED);
        return result;
    }

    private ComplianceProcessingResult processAMLAlert(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Analyze AML indicators
        AMLAnalysisResult analysis = amlService.analyzeAlert(
            event.getEntityId(),
            event.getAmount(),
            event.getMetadata()
        );
        
        result.setAmlAnalysis(analysis);
        
        if (analysis.requiresSAR()) {
            result.addViolation(new ComplianceViolation(
                "SAR_REQUIRED",
                "Suspicious Activity Report required",
                "HIGH"
            ));
            
            // Initiate SAR filing process
            regulatoryService.initiateSARFiling(event);
        }
        
        if (analysis.requiresInvestigation()) {
            result.addViolation(new ComplianceViolation(
                "INVESTIGATION_REQUIRED",
                "Manual investigation required",
                "MEDIUM"
            ));
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private ComplianceProcessingResult processFraudAlert(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Coordinate with fraud detection system
        FraudAnalysisResult fraudAnalysis = fraudService.analyzeFraudAlert(
            event.getEntityId(),
            event.getMetadata()
        );
        
        result.setFraudAnalysis(fraudAnalysis);
        
        if (fraudAnalysis.isConfirmedFraud()) {
            result.addViolation(new ComplianceViolation(
                "CONFIRMED_FRAUD",
                "Fraud confirmed - compliance action required",
                "CRITICAL"
            ));
            
            // File SAR for confirmed fraud
            regulatoryService.initiateFraudSAR(event, fraudAnalysis);
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private ComplianceProcessingResult processSuspiciousActivity(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Analyze suspicious activity patterns
        SuspiciousActivityAnalysis analysis = amlService.analyzeSuspiciousActivity(
            event.getEntityId(),
            event.getMetadata()
        );
        
        result.setSuspiciousActivityAnalysis(analysis);
        
        if (analysis.requiresReporting()) {
            result.addViolation(new ComplianceViolation(
                "SUSPICIOUS_ACTIVITY_REPORTING",
                "Suspicious activity requires regulatory reporting",
                "HIGH"
            ));
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private ComplianceProcessingResult processRegulatoryReport(ComplianceEvent event) {
        ComplianceProcessingResult result = new ComplianceProcessingResult();
        
        // Generate or update regulatory report
        RegulatoryReportResult reportResult = regulatoryService.processReport(
            event.getDescription(),
            event.getMetadata()
        );
        
        result.setReportResult(reportResult);
        
        if (!reportResult.isValid()) {
            result.addViolation(new ComplianceViolation(
                "INVALID_REPORT",
                "Regulatory report validation failed: " + reportResult.getValidationErrors(),
                "MEDIUM"
            ));
        }
        
        result.setStatus(ProcessingStatus.COMPLETED);
        return result;
    }

    private void performAMLScreening(ComplianceEvent event, ComplianceProcessingResult result) {
        AMLScreeningResult amlResult = amlService.performScreening(
            event.getEntityId(),
            event.getEntityType(),
            event.getAmount(),
            event.getCountry()
        );
        
        result.setAmlScreeningResult(amlResult);
        
        if (amlResult.hasRiskIndicators()) {
            for (RiskIndicator indicator : amlResult.getRiskIndicators()) {
                result.addViolation(new ComplianceViolation(
                    "AML_RISK_INDICATOR",
                    "AML risk indicator detected: " + indicator.getDescription(),
                    indicator.getSeverity()
                ));
            }
        }
    }

    private void performSanctionsScreening(ComplianceEvent event, ComplianceProcessingResult result) {
        SanctionsScreeningResult sanctionsResult = sanctionsService.performScreening(
            event.getEntityId(),
            event.getEntityType(),
            event.getCountry()
        );
        
        result.setSanctionsScreeningResult(sanctionsResult);
        
        if (sanctionsResult.hasMatches()) {
            for (SanctionsMatch match : sanctionsResult.getMatches()) {
                result.addViolation(new ComplianceViolation(
                    "SANCTIONS_MATCH",
                    "Sanctions match: " + match.getMatchDescription(),
                    match.getSeverity()
                ));
            }
        }
    }

    private void generateRegulatoryReports(ComplianceEvent event, ComplianceProcessingResult result) {
        // Determine required reports
        List<String> requiredReports = determineRequiredReports(event, result);
        
        for (String reportType : requiredReports) {
            CompletableFuture.runAsync(() -> {
                try {
                    regulatoryService.generateReport(reportType, event, result);
                } catch (Exception e) {
                    log.error("Failed to generate {} report for event {}", reportType, event.getEventId(), e);
                }
            });
        }
    }

    private List<String> determineRequiredReports(ComplianceEvent event, ComplianceProcessingResult result) {
        List<String> reports = new ArrayList<>();
        
        // CTR - Currency Transaction Report
        if (event.getAmount() != null && event.getAmount().compareTo(CTR_THRESHOLD) >= 0) {
            reports.add("CTR");
        }
        
        // SAR - Suspicious Activity Report
        if (result.hasViolationsOfSeverity("HIGH") || result.hasViolationsOfSeverity("CRITICAL")) {
            reports.add("SAR");
        }
        
        // OFAC Report
        if (result.hasSanctionsViolations()) {
            reports.add("OFAC");
        }
        
        // FinCEN Report
        if (event.getEventType() == ComplianceEventType.AML_ALERT) {
            reports.add("FINCEN");
        }
        
        return reports;
    }

    private void handleComplianceViolations(ComplianceEvent event, ComplianceProcessingResult result) {
        for (ComplianceViolation violation : result.getViolations()) {
            // Create compliance case
            ComplianceCase complianceCase = ComplianceCase.builder()
                .caseId(UUID.randomUUID().toString())
                .eventId(event.getEventId())
                .violationType(violation.getViolationType())
                .severity(violation.getSeverity())
                .description(violation.getDescription())
                .entityId(event.getEntityId())
                .entityType(event.getEntityType())
                .status("OPEN")
                .createdAt(Instant.now())
                .assignedTo(getAssigneeForViolation(violation))
                .build();
            
            complianceCaseService.createCase(complianceCase);
            
            // Take immediate action for critical violations
            if ("CRITICAL".equals(violation.getSeverity())) {
                takeImmediateAction(event, violation);
            }
        }
    }

    private String getAssigneeForViolation(ComplianceViolation violation) {
        switch (violation.getViolationType()) {
            case "SANCTIONS_MATCH":
            case "SANCTIONS_HIT_CONFIRMED":
                return "SANCTIONS_TEAM";
            case "SAR_REQUIRED":
            case "AML_RISK_INDICATOR":
                return "AML_TEAM";
            case "FRAUD_DETECTED":
            case "CONFIRMED_FRAUD":
                return "FRAUD_TEAM";
            default:
                return "COMPLIANCE_TEAM";
        }
    }

    private void takeImmediateAction(ComplianceEvent event, ComplianceViolation violation) {
        switch (violation.getViolationType()) {
            case "SANCTIONS_HIT_CONFIRMED":
            case "SANCTIONS_MATCH":
                // Block all transactions and freeze accounts
                if (event.getCustomerId() != null) {
                    accountService.freezeCustomerAccounts(event.getCustomerId(), violation.getViolationType());
                }
                if (event.getTransactionId() != null) {
                    transactionService.blockTransaction(event.getTransactionId(), violation.getViolationType());
                }
                break;
                
            case "CONFIRMED_FRAUD":
                // Block transaction and flag account
                if (event.getTransactionId() != null) {
                    transactionService.blockTransaction(event.getTransactionId(), "FRAUD");
                }
                if (event.getCustomerId() != null) {
                    accountService.flagAccount(event.getCustomerId(), "FRAUD_ALERT");
                }
                break;
        }
        
        // Send urgent notification
        notificationService.sendUrgentComplianceAlert(violation, event);
    }

    private void updateComplianceRecords(ComplianceEvent event, ComplianceProcessingResult result) {
        // Save compliance event record
        ComplianceEventRecord record = ComplianceEventRecord.builder()
            .eventId(event.getEventId())
            .eventType(event.getEventType())
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .processingResult(result.toJson())
            .status(result.getStatus())
            .violationCount(result.getViolations().size())
            .processedAt(Instant.now())
            .build();
        
        complianceEventRepository.save(record);
        
        // Update entity compliance profile
        complianceProfileService.updateProfile(
            event.getEntityId(),
            event.getEntityType(),
            result
        );
    }

    private void sendComplianceNotifications(ComplianceEvent event, ComplianceProcessingResult result) {
        Map<String, Object> notificationData = Map.of(
            "eventId", event.getEventId(),
            "eventType", event.getEventType().toString(),
            "entityId", event.getEntityId(),
            "status", result.getStatus().toString(),
            "violationCount", result.getViolations().size(),
            "severity", result.getHighestSeverity()
        );
        
        // Send based on severity and result
        if (result.hasViolationsOfSeverity("CRITICAL")) {
            notificationService.sendCriticalComplianceAlert(notificationData);
            
            // Page compliance officer for critical violations
            notificationService.pageComplianceOfficer(
                "Critical compliance violation detected",
                event.getEventId()
            );
            
        } else if (result.hasViolationsOfSeverity("HIGH")) {
            notificationService.sendHighPriorityComplianceAlert(notificationData);
            
        } else if (result.hasViolations()) {
            notificationService.sendStandardComplianceAlert(notificationData);
        }
        
        // Send summary to compliance dashboard
        dashboardService.updateComplianceDashboard(event, result);
    }

    private void auditComplianceEvent(ComplianceEvent event, ComplianceProcessingResult result, 
                                     GenericKafkaEvent originalEvent) {
        auditService.auditComplianceEvent(
            event.getEventId(),
            event.getEventType().toString(),
            event.getEntityId(),
            result.getStatus().toString(),
            result.getViolations().size(),
            originalEvent.getEventId()
        );
    }

    private void recordComplianceMetrics(ComplianceEvent event, ComplianceProcessingResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordComplianceMetrics(
            event.getEventType().toString(),
            result.getStatus().toString(),
            result.getViolations().size(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record compliance KPIs
        metricsService.recordComplianceKPIs(
            event.getEventType(),
            result.hasViolations(),
            result.getHighestSeverity()
        );
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("compliance-validation-errors", event);
    }

    private void handleComplianceViolation(GenericKafkaEvent event, ComplianceViolationException e) {
        // Create high-priority compliance case
        alertService.createCriticalAlert(
            "COMPLIANCE_PROCESSING_VIOLATION",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("compliance-critical-violations", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying compliance event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("compliance-events-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for compliance event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "compliance-events");
        
        kafkaTemplate.send("compliance-events.DLQ", event);
        
        alertingService.createDLQAlert(
            "compliance-events",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleComplianceEventFailure(GenericKafkaEvent event, String topic, int partition,
                                            long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for compliance event processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Compliance Event Circuit Breaker Open",
            "Compliance event processing is failing. Regulatory compliance at risk."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class ComplianceViolationException extends RuntimeException {
        public ComplianceViolationException(String message) {
            super(message);
        }
    }
}