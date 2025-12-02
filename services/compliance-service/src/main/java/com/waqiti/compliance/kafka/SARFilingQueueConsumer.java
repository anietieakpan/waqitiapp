package com.waqiti.compliance.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.compliance.config.InstitutionProperties;
import com.waqiti.compliance.model.*;
import com.waqiti.compliance.repository.SARRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for SAR (Suspicious Activity Report) filing queue
 * Handles automated and manual SAR generation, validation, and submission to FinCEN
 * 
 * Critical for: Regulatory compliance, BSA requirements, anti-money laundering
 * SLA: Must file SARs within 30 days of detection, urgent SARs within 3 days
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SARFilingQueueConsumer {

    private final SARRepository sarRepository;
    private final SARGenerationService sarGenerationService;
    private final FinCENService finCENService;
    private final RegulatoryReportingService regulatoryService;
    private final ComplianceValidationService validationService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final InstitutionProperties institutionProperties;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long STANDARD_SAR_DEADLINE_DAYS = 30;
    private static final long URGENT_SAR_DEADLINE_DAYS = 3;
    private static final long CONTINUING_ACTIVITY_DEADLINE_DAYS = 120;
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000.00");
    
    @KafkaListener(
        topics = {"sar-filing-queue"},
        groupId = "sar-filing-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "sar-filing-processor", fallbackMethod = "handleSARFilingFailure")
    @Retry(name = "sar-filing-processor")
    public void processSARFilingRequest(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing SAR filing request: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            SARFilingRequest sarRequest = extractSARFilingRequest(payload);
            
            // Validate SAR filing request
            validateSARRequest(sarRequest);
            
            // Check for duplicate SAR
            if (isDuplicateSAR(sarRequest)) {
                log.warn("Duplicate SAR filing request detected: {}, skipping", sarRequest.getSarId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Determine SAR type and urgency
            SARClassification classification = classifySAR(sarRequest);
            
            // Gather supporting evidence and documentation
            SAREvidence evidence = gatherEvidence(sarRequest);
            
            // Generate SAR document
            SARDocument sarDocument = generateSARDocument(sarRequest, classification, evidence);
            
            // Validate SAR completeness and accuracy
            SARValidationResult validation = validateSARDocument(sarDocument);
            
            // Handle validation failures
            if (!validation.isValid()) {
                handleValidationFailures(sarRequest, validation);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process SAR based on classification
            SARProcessingResult result = processSAR(sarRequest, sarDocument, classification);
            
            // Submit to FinCEN if approved
            if (result.isApprovedForSubmission()) {
                submitToFinCEN(sarDocument, result);
            }
            
            // Handle continuing activity tracking
            if (classification.isContinuingActivity()) {
                scheduleContinuingActivityUpdates(sarRequest, sarDocument);
            }
            
            // Send notifications
            sendSARNotifications(sarRequest, result);
            
            // Update SAR tracking
            updateSARTracking(sarRequest, sarDocument, result);
            
            // Audit SAR processing
            auditSARProcessing(sarRequest, result, event);
            
            // Record metrics
            recordSARMetrics(sarRequest, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed SAR filing: {} type: {} status: {} in {}ms", 
                    sarRequest.getSarId(), classification.getSarType(), result.getStatus(),
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for SAR filing: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (RegulatoryException e) {
            log.error("Regulatory validation failed for SAR filing: {}", eventId, e);
            handleRegulatoryError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process SAR filing: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private SARFilingRequest extractSARFilingRequest(Map<String, Object> payload) {
        return SARFilingRequest.builder()
            .sarId(extractString(payload, "sarId", UUID.randomUUID().toString()))
            .incidentDate(extractLocalDate(payload, "incidentDate"))
            .detectionDate(extractLocalDate(payload, "detectionDate"))
            .subjectId(extractString(payload, "subjectId", null))
            .subjectType(extractString(payload, "subjectType", null))
            .subjectName(extractString(payload, "subjectName", null))
            .suspiciousActivity(extractString(payload, "suspiciousActivity", null))
            .activityType(SARActivityType.fromString(extractString(payload, "activityType", "OTHER")))
            .transactionIds(extractStringList(payload, "transactionIds"))
            .totalAmount(extractBigDecimal(payload, "totalAmount"))
            .currency(extractString(payload, "currency", "USD"))
            .locationOfActivity(extractString(payload, "locationOfActivity", null))
            .narrative(extractString(payload, "narrative", null))
            .filingReason(extractString(payload, "filingReason", null))
            .urgency(extractString(payload, "urgency", "STANDARD"))
            .reportingOfficer(extractString(payload, "reportingOfficer", null))
            .continuingActivity(extractBoolean(payload, "continuingActivity", false))
            .priorSARNumber(extractString(payload, "priorSARNumber", null))
            .lawEnforcementContact(extractString(payload, "lawEnforcementContact", null))
            .metadata(extractMap(payload, "metadata"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateSARRequest(SARFilingRequest request) {
        if (request.getIncidentDate() == null) {
            throw new ValidationException("Incident date is required for SAR filing");
        }
        
        if (request.getDetectionDate() == null) {
            throw new ValidationException("Detection date is required for SAR filing");
        }
        
        if (request.getDetectionDate().isBefore(request.getIncidentDate())) {
            throw new ValidationException("Detection date cannot be before incident date");
        }
        
        if (request.getSubjectId() == null || request.getSubjectId().isEmpty()) {
            throw new ValidationException("Subject ID is required for SAR filing");
        }
        
        if (request.getSubjectType() == null || request.getSubjectType().isEmpty()) {
            throw new ValidationException("Subject type is required for SAR filing");
        }
        
        if (request.getSuspiciousActivity() == null || request.getSuspiciousActivity().trim().isEmpty()) {
            throw new ValidationException("Suspicious activity description is required");
        }
        
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Valid total amount is required for SAR filing");
        }
        
        // Check SAR threshold
        if (request.getTotalAmount().compareTo(SAR_THRESHOLD) < 0 && !isExemptFromThreshold(request)) {
            log.warn("SAR amount {} below threshold {}, validating exemption", 
                    request.getTotalAmount(), SAR_THRESHOLD);
        }
        
        // Validate timeline requirements
        validateFilingTimeline(request);
        
        // Validate continuing activity requirements
        if (request.isContinuingActivity() && 
            (request.getPriorSARNumber() == null || request.getPriorSARNumber().isEmpty())) {
            throw new ValidationException("Prior SAR number required for continuing activity");
        }
    }

    private boolean isExemptFromThreshold(SARFilingRequest request) {
        // Certain activity types are exempt from the $5,000 threshold
        return Arrays.asList(
            SARActivityType.TERRORIST_FINANCING,
            SARActivityType.TRADE_BASED_MONEY_LAUNDERING,
            SARActivityType.CYBER_ENABLED_CRIME,
            SARActivityType.ELDER_FINANCIAL_ABUSE
        ).contains(request.getActivityType());
    }

    private void validateFilingTimeline(SARFilingRequest request) {
        LocalDate currentDate = LocalDate.now();
        long daysSinceDetection = ChronoUnit.DAYS.between(request.getDetectionDate(), currentDate);
        
        // Check if filing deadline has passed
        if ("URGENT".equals(request.getUrgency()) && daysSinceDetection > URGENT_SAR_DEADLINE_DAYS) {
            throw new RegulatoryException("Urgent SAR filing deadline exceeded: " + 
                daysSinceDetection + " days since detection");
        }
        
        if (daysSinceDetection > STANDARD_SAR_DEADLINE_DAYS) {
            throw new RegulatoryException("Standard SAR filing deadline exceeded: " + 
                daysSinceDetection + " days since detection");
        }
        
        // Warning for approaching deadline
        if (daysSinceDetection > (STANDARD_SAR_DEADLINE_DAYS - 5)) {
            log.warn("SAR filing approaching deadline: {} days since detection", daysSinceDetection);
        }
    }

    private boolean isDuplicateSAR(SARFilingRequest request) {
        // Check for exact duplicate
        if (sarRepository.existsBySarIdAndCreatedAtAfter(
                request.getSarId(), 
                Instant.now().minus(24, ChronoUnit.HOURS))) {
            return true;
        }
        
        // Check for similar SAR (same subject, similar timeframe)
        return sarRepository.existsSimilarSAR(
            request.getSubjectId(),
            request.getIncidentDate(),
            request.getActivityType()
        );
    }

    private SARClassification classifySAR(SARFilingRequest request) {
        SARClassification classification = new SARClassification();
        
        // Determine SAR type based on activity
        String sarType = determineSARType(request.getActivityType(), request.getSuspiciousActivity());
        classification.setSarType(sarType);
        
        // Determine priority
        String priority = determinePriority(request);
        classification.setPriority(priority);
        
        // Determine review requirements
        classification.setRequiresManagerialReview(requiresManagerialReview(request));
        classification.setRequiresLegalReview(requiresLegalReview(request));
        classification.setRequiresExecutiveApproval(requiresExecutiveApproval(request));
        
        // Determine special handling
        classification.setContinuingActivity(request.isContinuingActivity());
        classification.setLawEnforcementNotification(requiresLawEnforcementNotification(request));
        classification.setMediaSensitive(isMediaSensitive(request));
        
        // Calculate filing deadline
        LocalDate deadline = calculateFilingDeadline(request, classification);
        classification.setFilingDeadline(deadline);
        
        return classification;
    }

    private String determineSARType(SARActivityType activityType, String description) {
        switch (activityType) {
            case MONEY_LAUNDERING:
                return "MONEY_LAUNDERING";
            case TERRORIST_FINANCING:
                return "TERRORIST_FINANCING";
            case FRAUD:
                return description.toLowerCase().contains("check") ? "CHECK_FRAUD" : "WIRE_FRAUD";
            case STRUCTURING:
                return "STRUCTURING";
            case IDENTITY_THEFT:
                return "IDENTITY_THEFT";
            case ELDER_FINANCIAL_ABUSE:
                return "ELDER_ABUSE";
            case CYBER_ENABLED_CRIME:
                return "CYBER_CRIME";
            case TRADE_BASED_MONEY_LAUNDERING:
                return "TRADE_BASED_ML";
            case BRIBERY_CORRUPTION:
                return "BRIBERY_CORRUPTION";
            default:
                return "OTHER_SUSPICIOUS";
        }
    }

    private String determinePriority(SARFilingRequest request) {
        // Critical priority conditions
        if (Arrays.asList(SARActivityType.TERRORIST_FINANCING, SARActivityType.CYBER_ENABLED_CRIME)
                .contains(request.getActivityType()) ||
            "URGENT".equals(request.getUrgency()) ||
            request.getTotalAmount().compareTo(new BigDecimal("100000")) > 0) {
            return "CRITICAL";
        }
        
        // High priority conditions
        if (request.getTotalAmount().compareTo(new BigDecimal("25000")) > 0 ||
            request.getLawEnforcementContact() != null ||
            Arrays.asList(SARActivityType.MONEY_LAUNDERING, SARActivityType.FRAUD)
                .contains(request.getActivityType())) {
            return "HIGH";
        }
        
        return "STANDARD";
    }

    private boolean requiresManagerialReview(SARFilingRequest request) {
        return request.getTotalAmount().compareTo(new BigDecimal("50000")) > 0 ||
               Arrays.asList(SARActivityType.TERRORIST_FINANCING, SARActivityType.MONEY_LAUNDERING)
                   .contains(request.getActivityType()) ||
               request.getLawEnforcementContact() != null;
    }

    private boolean requiresLegalReview(SARFilingRequest request) {
        return Arrays.asList(SARActivityType.TERRORIST_FINANCING, SARActivityType.BRIBERY_CORRUPTION)
                   .contains(request.getActivityType()) ||
               request.getTotalAmount().compareTo(new BigDecimal("100000")) > 0;
    }

    private boolean requiresExecutiveApproval(SARFilingRequest request) {
        return request.getActivityType() == SARActivityType.TERRORIST_FINANCING ||
               request.getTotalAmount().compareTo(new BigDecimal("250000")) > 0;
    }

    private boolean requiresLawEnforcementNotification(SARFilingRequest request) {
        return Arrays.asList(SARActivityType.TERRORIST_FINANCING, SARActivityType.CYBER_ENABLED_CRIME)
                   .contains(request.getActivityType()) ||
               request.getLawEnforcementContact() != null;
    }

    private boolean isMediaSensitive(SARFilingRequest request) {
        // Check if subject is a public figure or high-profile entity
        return subjectScreeningService.isPublicFigure(request.getSubjectId()) ||
               request.getTotalAmount().compareTo(new BigDecimal("500000")) > 0;
    }

    private LocalDate calculateFilingDeadline(SARFilingRequest request, SARClassification classification) {
        if ("URGENT".equals(request.getUrgency()) || "CRITICAL".equals(classification.getPriority())) {
            return request.getDetectionDate().plusDays(URGENT_SAR_DEADLINE_DAYS);
        } else if (request.isContinuingActivity()) {
            return request.getDetectionDate().plusDays(CONTINUING_ACTIVITY_DEADLINE_DAYS);
        } else {
            return request.getDetectionDate().plusDays(STANDARD_SAR_DEADLINE_DAYS);
        }
    }

    private SAREvidence gatherEvidence(SARFilingRequest request) {
        SAREvidence evidence = new SAREvidence();
        evidence.setSarId(request.getSarId());
        evidence.setGatheringStartTime(Instant.now());
        
        // Gather transaction evidence
        if (request.getTransactionIds() != null && !request.getTransactionIds().isEmpty()) {
            List<TransactionEvidence> transactions = transactionService.getTransactionEvidence(
                request.getTransactionIds()
            );
            evidence.setTransactionEvidence(transactions);
        }
        
        // Gather subject information
        SubjectEvidence subjectInfo = subjectService.getSubjectEvidence(
            request.getSubjectId(),
            request.getSubjectType()
        );
        evidence.setSubjectEvidence(subjectInfo);
        
        // Gather account evidence
        if (subjectInfo.getAccountIds() != null) {
            List<AccountEvidence> accounts = accountService.getAccountEvidence(
                subjectInfo.getAccountIds()
            );
            evidence.setAccountEvidence(accounts);
        }
        
        // Gather supporting documents
        List<String> supportingDocs = documentService.gatherSupportingDocuments(
            request.getSubjectId(),
            request.getIncidentDate(),
            request.getActivityType()
        );
        evidence.setSupportingDocuments(supportingDocs);
        
        // Perform additional investigations
        InvestigationResult investigation = investigationService.performSARInvestigation(
            request.getSubjectId(),
            request.getActivityType(),
            request.getIncidentDate()
        );
        evidence.setInvestigationResult(investigation);
        
        evidence.setGatheringEndTime(Instant.now());
        evidence.setComplete(validateEvidenceCompleteness(evidence));
        
        return evidence;
    }

    private boolean validateEvidenceCompleteness(SAREvidence evidence) {
        // Check required evidence components
        return evidence.getSubjectEvidence() != null &&
               evidence.getTransactionEvidence() != null && !evidence.getTransactionEvidence().isEmpty() &&
               evidence.getInvestigationResult() != null;
    }

    private SARDocument generateSARDocument(SARFilingRequest request, SARClassification classification, 
                                           SAREvidence evidence) {
        
        SARGenerationRequest generationRequest = SARGenerationRequest.builder()
            .sarRequest(request)
            .classification(classification)
            .evidence(evidence)
            .build();
        
        SARDocument document = sarGenerationService.generateSAR(generationRequest);
        
        // Enrich document with additional required fields
        enrichSARDocument(document, request, evidence);
        
        // Generate BSA form fields
        Map<String, Object> bsaFormData = generateBSAFormData(document, request, evidence);
        document.setBsaFormData(bsaFormData);
        
        return document;
    }

    private void enrichSARDocument(SARDocument document, SARFilingRequest request, SAREvidence evidence) {
        // CRITICAL FIX: Use configured institution information instead of hardcoded values
        // Validate that institution EIN is properly configured (not placeholder)
        if (!institutionProperties.isEinConfigured()) {
            String errorMsg = String.format(
                "CRITICAL: Institution EIN is not properly configured. Current value: %s. " +
                "SAR filings will be rejected by FinCEN. Please configure a valid EIN in production environment.",
                institutionProperties.getEin()
            );
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // Set financial institution information from configuration
        document.setFinancialInstitutionName(institutionProperties.getName());
        document.setFinancialInstitutionEIN(institutionProperties.getEin());
        document.setFinancialInstitutionAddress(institutionProperties.getAddress().getFormattedAddress());

        // Set contact information from configuration
        document.setContactName(institutionProperties.getContact().getComplianceOfficerName());
        document.setContactTitle(institutionProperties.getContact().getComplianceOfficerTitle());
        document.setContactPhone(institutionProperties.getContact().getComplianceOfficerPhone());
        document.setContactEmail(institutionProperties.getContact().getComplianceOfficerEmail());

        // Set regulatory information from configuration
        document.setFilingInstitutionCode(institutionProperties.getRegulatory().getFilingInstitutionCode());
        document.setPrimaryRegulator(institutionProperties.getRegulatory().getPrimaryRegulator());
        document.setCharterType(institutionProperties.getRegulatory().getCharterType());
        if (institutionProperties.getRegulatory().getRssdId() != null && !institutionProperties.getRegulatory().getRssdId().isEmpty()) {
            document.setRssdId(institutionProperties.getRegulatory().getRssdId());
        }
        document.setReportingPeriod(calculateReportingPeriod(request.getIncidentDate()));

        // Set subject information from evidence
        if (evidence.getSubjectEvidence() != null) {
            document.setSubjectSSN(evidence.getSubjectEvidence().getSsn());
            document.setSubjectDateOfBirth(evidence.getSubjectEvidence().getDateOfBirth());
            document.setSubjectAddress(evidence.getSubjectEvidence().getAddress());
            document.setSubjectOccupation(evidence.getSubjectEvidence().getOccupation());
        }

        // Log successful configuration usage for audit trail
        log.info("SAR document enriched with institution info - Name: {}, EIN: {}, Filing Code: {}",
            institutionProperties.getName(),
            maskEIN(institutionProperties.getEin()),  // Mask EIN in logs for security
            institutionProperties.getRegulatory().getFilingInstitutionCode()
        );
    }

    /**
     * Masks EIN for logging (shows only last 4 digits)
     * Example: 12-3456789 becomes XX-XXX6789
     */
    private String maskEIN(String ein) {
        if (ein == null || ein.length() < 4) {
            return "XX-XXXXXXX";
        }
        return "XX-XXX" + ein.substring(ein.length() - 4);
    }

    private Map<String, Object> generateBSAFormData(SARDocument document, SARFilingRequest request, 
                                                   SAREvidence evidence) {
        Map<String, Object> formData = new HashMap<>();
        
        // Part I - Subject Information
        formData.put("subject_name", request.getSubjectName());
        formData.put("subject_ssn", document.getSubjectSSN());
        formData.put("subject_dob", document.getSubjectDateOfBirth());
        formData.put("subject_address", document.getSubjectAddress());
        formData.put("subject_occupation", document.getSubjectOccupation());
        
        // Part II - Suspicious Activity Information
        formData.put("activity_date", request.getIncidentDate());
        formData.put("activity_amount", request.getTotalAmount());
        formData.put("activity_type", request.getActivityType().getCode());
        formData.put("activity_location", request.getLocationOfActivity());
        
        // Part III - Transaction Information
        if (evidence.getTransactionEvidence() != null) {
            formData.put("transaction_count", evidence.getTransactionEvidence().size());
            formData.put("transaction_details", evidence.getTransactionEvidence());
        }
        
        // Part IV - Financial Institution Information
        formData.put("institution_name", document.getFinancialInstitutionName());
        formData.put("institution_ein", document.getFinancialInstitutionEIN());
        formData.put("institution_address", document.getFinancialInstitutionAddress());
        
        // Part V - Narrative
        formData.put("narrative", document.getNarrative());
        formData.put("filing_reason", request.getFilingReason());
        
        return formData;
    }

    private SARValidationResult validateSARDocument(SARDocument document) {
        SARValidationResult result = validationService.validateSAR(document);
        
        // Additional business rule validations
        List<String> additionalErrors = new ArrayList<>();
        
        // Validate narrative length (minimum 200 characters)
        if (document.getNarrative() == null || document.getNarrative().length() < 200) {
            additionalErrors.add("Narrative must be at least 200 characters");
        }
        
        // Validate required subject information
        if (document.getSubjectSSN() == null && document.getSubjectDateOfBirth() == null) {
            additionalErrors.add("Either SSN or date of birth required for subject identification");
        }
        
        // Validate transaction information completeness
        if (document.getBsaFormData().get("transaction_count") == null ||
            (Integer) document.getBsaFormData().get("transaction_count") == 0) {
            additionalErrors.add("At least one transaction must be included in SAR");
        }
        
        // Add additional errors to result
        result.getValidationErrors().addAll(additionalErrors);
        result.setValid(result.getValidationErrors().isEmpty());
        
        return result;
    }

    private void handleValidationFailures(SARFilingRequest request, SARValidationResult validation) {
        // Create validation failure case
        ComplianceCase validationCase = ComplianceCase.builder()
            .caseId(UUID.randomUUID().toString())
            .caseType("SAR_VALIDATION_FAILURE")
            .sarId(request.getSarId())
            .priority("HIGH")
            .status("OPEN")
            .description("SAR validation failed: " + String.join(", ", validation.getValidationErrors()))
            .assignedTo(request.getReportingOfficer())
            .createdAt(Instant.now())
            .build();
        
        caseManagementService.createCase(validationCase);
        
        // Notify compliance team
        notificationService.sendValidationFailureNotification(
            request.getReportingOfficer(),
            request.getSarId(),
            validation.getValidationErrors()
        );
        
        // Update SAR status
        SARRecord sarRecord = SARRecord.builder()
            .sarId(request.getSarId())
            .status(SARStatus.VALIDATION_FAILED)
            .validationErrors(validation.getValidationErrors())
            .createdAt(Instant.now())
            .build();
        
        sarRepository.save(sarRecord);
    }

    private SARProcessingResult processSAR(SARFilingRequest request, SARDocument document, 
                                          SARClassification classification) {
        
        SARProcessingResult result = new SARProcessingResult();
        result.setSarId(request.getSarId());
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Create SAR record
            SARRecord sarRecord = createSARRecord(request, document, classification);
            result.setSarRecord(sarRecord);
            
            // Route for required approvals
            if (classification.isRequiresManagerialReview()) {
                routeForManagerialReview(sarRecord, classification);
                result.setPendingManagerialReview(true);
            }
            
            if (classification.isRequiresLegalReview()) {
                routeForLegalReview(sarRecord, classification);
                result.setPendingLegalReview(true);
            }
            
            if (classification.isRequiresExecutiveApproval()) {
                routeForExecutiveApproval(sarRecord, classification);
                result.setPendingExecutiveApproval(true);
            }
            
            // Determine if ready for submission
            boolean readyForSubmission = !result.isPendingManagerialReview() && 
                                       !result.isPendingLegalReview() && 
                                       !result.isPendingExecutiveApproval();
            
            result.setApprovedForSubmission(readyForSubmission);
            
            if (readyForSubmission) {
                result.setStatus(SARStatus.APPROVED_FOR_FILING);
            } else {
                result.setStatus(SARStatus.PENDING_APPROVAL);
            }
            
        } catch (Exception e) {
            log.error("Failed to process SAR: {}", request.getSarId(), e);
            result.setStatus(SARStatus.PROCESSING_FAILED);
            result.setErrorMessage(e.getMessage());
        }
        
        result.setProcessingEndTime(Instant.now());
        return result;
    }

    private SARRecord createSARRecord(SARFilingRequest request, SARDocument document, 
                                     SARClassification classification) {
        
        SARRecord record = SARRecord.builder()
            .sarId(request.getSarId())
            .sarNumber(generateSARNumber())
            .activityType(request.getActivityType())
            .incidentDate(request.getIncidentDate())
            .detectionDate(request.getDetectionDate())
            .subjectId(request.getSubjectId())
            .subjectName(request.getSubjectName())
            .totalAmount(request.getTotalAmount())
            .currency(request.getCurrency())
            .filingDeadline(classification.getFilingDeadline())
            .priority(classification.getPriority())
            .status(SARStatus.DRAFT)
            .continuingActivity(request.isContinuingActivity())
            .priorSARNumber(request.getPriorSARNumber())
            .reportingOfficer(request.getReportingOfficer())
            .documentPath(documentService.storeSARDocument(document))
            .createdAt(Instant.now())
            .createdBy(request.getRequestedBy())
            .build();
        
        return sarRepository.save(record);
    }

    private String generateSARNumber() {
        String year = String.valueOf(LocalDate.now().getYear());
        String sequence = sarRepository.getNextSequenceNumber(year);
        return "SAR-" + year + "-" + String.format("%06d", Integer.parseInt(sequence));
    }

    private void routeForManagerialReview(SARRecord sarRecord, SARClassification classification) {
        ReviewRequest reviewRequest = ReviewRequest.builder()
            .sarId(sarRecord.getSarId())
            .reviewType("MANAGERIAL")
            .assignedTo(getManagerForReview(classification.getPriority()))
            .deadline(Instant.now().plus(2, ChronoUnit.DAYS))
            .priority(classification.getPriority())
            .createdAt(Instant.now())
            .build();
        
        reviewService.submitForReview(reviewRequest);
    }

    private void routeForLegalReview(SARRecord sarRecord, SARClassification classification) {
        ReviewRequest reviewRequest = ReviewRequest.builder()
            .sarId(sarRecord.getSarId())
            .reviewType("LEGAL")
            .assignedTo(getLegalReviewer())
            .deadline(Instant.now().plus(3, ChronoUnit.DAYS))
            .priority(classification.getPriority())
            .createdAt(Instant.now())
            .build();
        
        reviewService.submitForReview(reviewRequest);
    }

    private void routeForExecutiveApproval(SARRecord sarRecord, SARClassification classification) {
        ReviewRequest reviewRequest = ReviewRequest.builder()
            .sarId(sarRecord.getSarId())
            .reviewType("EXECUTIVE")
            .assignedTo(getExecutiveApprover())
            .deadline(Instant.now().plus(1, ChronoUnit.DAYS))
            .priority("CRITICAL")
            .createdAt(Instant.now())
            .build();
        
        reviewService.submitForReview(reviewRequest);
    }

    private void submitToFinCEN(SARDocument document, SARProcessingResult result) {
        try {
            FinCENSubmissionRequest submission = FinCENSubmissionRequest.builder()
                .sarId(document.getSarId())
                .sarNumber(result.getSarRecord().getSarNumber())
                .documentContent(document.toXML())
                .bsaFormData(document.getBsaFormData())
                .submissionType("ELECTRONIC")
                .build();
            
            FinCENSubmissionResult submissionResult = finCENService.submitSAR(submission);
            
            if (submissionResult.isSuccessful()) {
                // Update SAR record
                SARRecord record = result.getSarRecord();
                record.setStatus(SARStatus.FILED);
                record.setFiledAt(Instant.now());
                record.setFinCENConfirmationNumber(submissionResult.getConfirmationNumber());
                sarRepository.save(record);
                
                // Send success notification
                notificationService.sendSARFilingSuccessNotification(
                    record.getReportingOfficer(),
                    record.getSarNumber(),
                    submissionResult.getConfirmationNumber()
                );
                
            } else {
                // Handle submission failure
                handleSubmissionFailure(result.getSarRecord(), submissionResult);
            }
            
        } catch (Exception e) {
            log.error("Failed to submit SAR to FinCEN: {}", document.getSarId(), e);
            handleSubmissionFailure(result.getSarRecord(), null);
        }
    }

    private void handleSubmissionFailure(SARRecord record, FinCENSubmissionResult submissionResult) {
        record.setStatus(SARStatus.SUBMISSION_FAILED);
        record.setSubmissionErrors(submissionResult != null ? 
            submissionResult.getErrors() : Arrays.asList("Technical submission failure"));
        sarRepository.save(record);
        
        // Create high-priority case for manual intervention
        ComplianceCase failureCase = ComplianceCase.builder()
            .caseId(UUID.randomUUID().toString())
            .caseType("SAR_SUBMISSION_FAILURE")
            .sarId(record.getSarId())
            .priority("HIGH")
            .status("OPEN")
            .description("SAR submission to FinCEN failed - manual intervention required")
            .assignedTo("COMPLIANCE_MANAGER")
            .createdAt(Instant.now())
            .build();
        
        caseManagementService.createCase(failureCase);
        
        // Send alert
        alertService.createCriticalAlert(
            "SAR_SUBMISSION_FAILED",
            Map.of("sarId", record.getSarId(), "sarNumber", record.getSarNumber()),
            "SAR submission failed - regulatory deadline at risk"
        );
    }

    private void scheduleContinuingActivityUpdates(SARFilingRequest request, SARDocument document) {
        // Schedule quarterly updates for continuing activity SARs
        for (int quarter = 1; quarter <= 4; quarter++) {
            Instant updateTime = request.getCreatedAt().plus(quarter * 90L, ChronoUnit.DAYS);
            
            ContinuingActivityUpdate update = ContinuingActivityUpdate.builder()
                .originalSarId(request.getSarId())
                .updateNumber(quarter)
                .scheduledUpdateTime(updateTime)
                .status("SCHEDULED")
                .build();
            
            continuingActivityService.scheduleUpdate(update);
        }
    }

    private void sendSARNotifications(SARFilingRequest request, SARProcessingResult result) {
        Map<String, Object> notificationData = Map.of(
            "sarId", request.getSarId(),
            "sarNumber", result.getSarRecord() != null ? result.getSarRecord().getSarNumber() : "PENDING",
            "status", result.getStatus().toString(),
            "priority", result.getSarRecord() != null ? result.getSarRecord().getPriority() : "UNKNOWN",
            "filingDeadline", result.getSarRecord() != null ? 
                result.getSarRecord().getFilingDeadline() : LocalDate.now().plusDays(30)
        );
        
        // Notify reporting officer
        notificationService.sendSARStatusNotification(
            request.getReportingOfficer(),
            notificationData
        );
        
        // Notify compliance management for high-priority SARs
        if ("CRITICAL".equals(result.getSarRecord().getPriority()) || 
            "HIGH".equals(result.getSarRecord().getPriority())) {
            notificationService.sendComplianceManagementNotification(
                "SAR_HIGH_PRIORITY",
                notificationData
            );
        }
        
        // Update compliance dashboard
        dashboardService.updateSARDashboard(result.getSarRecord());
    }

    private void updateSARTracking(SARFilingRequest request, SARDocument document, 
                                  SARProcessingResult result) {
        
        if (result.getSarRecord() != null) {
            SARRecord record = result.getSarRecord();
            record.setLastUpdated(Instant.now());
            record.setProcessingResult(result.toJson());
            sarRepository.save(record);
        }
        
        // Update metrics
        metricsService.updateSARMetrics(request.getActivityType(), result.getStatus());
    }

    private void auditSARProcessing(SARFilingRequest request, SARProcessingResult result, 
                                   GenericKafkaEvent originalEvent) {
        auditService.auditSARProcessing(
            request.getSarId(),
            request.getActivityType().toString(),
            request.getSubjectId(),
            result.getStatus().toString(),
            request.getReportingOfficer(),
            originalEvent.getEventId()
        );
    }

    private void recordSARMetrics(SARFilingRequest request, SARProcessingResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordSARProcessingMetrics(
            request.getActivityType().toString(),
            result.getStatus().toString(),
            request.getTotalAmount(),
            processingTime
        );
        
        // Record compliance KPIs
        metricsService.recordSARComplianceMetrics(
            request.getActivityType(),
            ChronoUnit.DAYS.between(request.getDetectionDate(), LocalDate.now()),
            result.isApprovedForSubmission()
        );
    }

    // Helper methods for configuration
    private String getInstitutionAddress() {
        return "123 Financial District, New York, NY 10001";
    }

    private String getComplianceContactPhone() {
        return "+1-555-COMPLY";
    }

    private String getComplianceContactEmail() {
        return "compliance@example.com";
    }

    private String getFilingInstitutionCode() {
        return "31000";
    }

    private String calculateReportingPeriod(LocalDate incidentDate) {
        return incidentDate.getYear() + "Q" + ((incidentDate.getMonthValue() - 1) / 3 + 1);
    }

    private String getManagerForReview(String priority) {
        return "CRITICAL".equals(priority) ? "SENIOR_MANAGER" : "COMPLIANCE_MANAGER";
    }

    private String getLegalReviewer() {
        return "CHIEF_LEGAL_OFFICER";
    }

    private String getExecutiveApprover() {
        return "CHIEF_COMPLIANCE_OFFICER";
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("sar-filing-validation-errors", event);
    }

    private void handleRegulatoryError(GenericKafkaEvent event, RegulatoryException e) {
        // Create urgent compliance alert
        alertService.createUrgentAlert(
            "SAR_REGULATORY_VIOLATION",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("sar-filing-regulatory-violations", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying SAR filing {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("sar-filing-queue-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for SAR filing {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "sar-filing-queue");
        
        kafkaTemplate.send("sar-filing-queue.DLQ", event);
        
        alertingService.createDLQAlert(
            "sar-filing-queue",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleSARFilingFailure(GenericKafkaEvent event, String topic, int partition,
                                      long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for SAR filing: {}", e.getMessage());
        
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
            "SAR Filing Circuit Breaker Open",
            "SAR filing processing is failing. Regulatory compliance at risk."
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

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        return LocalDate.parse(value.toString());
    }

    private boolean extractBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
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

    public static class RegulatoryException extends RuntimeException {
        public RegulatoryException(String message) {
            super(message);
        }
    }
}