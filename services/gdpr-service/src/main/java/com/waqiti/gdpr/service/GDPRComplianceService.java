package com.waqiti.gdpr.service;

import com.waqiti.gdpr.domain.*;
import com.waqiti.gdpr.dto.*;
import com.waqiti.gdpr.repository.*;
import com.waqiti.common.encryption.EncryptionService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class GDPRComplianceService {

    private final UserDataRepository userDataRepository;
    private final ConsentRepository consentRepository;
    private final DataRequestRepository dataRequestRepository;
    private final AuditService auditService;
    private final EncryptionService encryptionService;
    private final NotificationService notificationService;
    private final DataExportService dataExportService;
    private final DataAnonymizationService anonymizationService;
    private final ConsentManagementService consentManagementService;
    private final DataSubjectRequestService dataSubjectRequestService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${gdpr.response-deadline-days:30}")
    private int gdprResponseDays;
    
    @Value("${gdpr.retention.default-years:7}")
    private int defaultRetentionYears;
    
    @Value("${gdpr.mfa.enabled:true}")
    private boolean mfaEnabled;
    
    @Value("${gdpr.encryption.algorithm:AES-256-GCM}")
    private String encryptionAlgorithm;
    
    private static final int GDPR_RESPONSE_DAYS = 30;
    private static final int DATA_RETENTION_DAYS = 2555; // 7 years
    
    private final Map<String, ProcessingActivity> activeProcessingActivities = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> requestDeadlines = new ConcurrentHashMap<>();

    /**
     * Handle data access request (GDPR Article 15)
     * Enhanced with MFA, comprehensive data collection, and processing information
     */
    @Transactional
    public DataAccessResponse handleDataAccessRequest(String userId, DataAccessRequest request) {
        log.info("Processing GDPR Article 15 data access request for user: {}", userId);
        
        try {
            // Enhanced identity verification with MFA
            if (mfaEnabled) {
                verifyIdentityWithMFA(userId, request.getVerificationData());
            } else if (!verifyUserIdentity(userId, request.getVerificationToken())) {
                throw new UnauthorizedException("Identity verification failed");
            }

            // Create request record with deadline tracking
            DataRequest dataRequest = DataRequest.builder()
                    .userId(userId)
                    .type(RequestType.ACCESS)
                    .status(RequestStatus.PROCESSING)
                    .requestedAt(LocalDateTime.now())
                    .deadline(LocalDateTime.now().plusDays(gdprResponseDays))
                    .build();
            
            dataRequestRepository.save(dataRequest);
            requestDeadlines.put(dataRequest.getId(), dataRequest.getDeadline());

            // Collect comprehensive user data from all systems
            UserDataPackage dataPackage = collectComprehensiveUserData(userId);
            
            // Include processing information as required by GDPR
            ProcessingInformation processingInfo = getProcessingInformation(userId);
            dataPackage.setProcessingInfo(processingInfo);
            
            // Include data recipients and transfers
            dataPackage.setRecipients(getDataRecipients(userId));
            dataPackage.setInternationalTransfers(getInternationalTransfers(userId));
            
            // Include retention periods
            dataPackage.setRetentionPeriods(getRetentionPeriods(userId));
            
            // Include automated decision information
            dataPackage.setAutomatedDecisions(getAutomatedDecisionInfo(userId));
            
            // Encrypt the package for security
            String encryptedPackage = encryptDataPackage(dataPackage, userId);
            
            // Generate secure download link with time limit
            String downloadUrl = dataExportService.generateSecureDownloadLink(
                userId, 
                encryptedPackage, 
                request.getFormat()
            );

            // Update request status
            dataRequest.setStatus(RequestStatus.COMPLETED);
            dataRequest.setCompletedAt(LocalDateTime.now());
            dataRequestRepository.save(dataRequest);

            // Comprehensive audit logging
            auditGDPRActivity(userId, "GDPR_ARTICLE_15_ACCESS", dataRequest);
            
            // Send notification to user
            notifyDataSubject(userId, "Your data access request is ready", downloadUrl);
            
            // Publish event for monitoring
            publishGDPREvent("DATA_ACCESS_COMPLETED", userId, dataRequest.getId());

            return DataAccessResponse.builder()
                    .requestId(dataRequest.getId())
                    .downloadUrl(downloadUrl)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .dataCategories(dataPackage.getCategories())
                    .processingPurposes(processingInfo.getPurposes())
                    .legalBasis(processingInfo.getLegalBasis())
                    .checksum(calculateChecksum(encryptedPackage))
                    .format(request.getFormat())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to process access request for user: {}", userId, e);
            auditGDPRActivity(userId, "ACCESS_REQUEST_FAILED", e.getMessage());
            throw new GDPRProcessingException("Failed to process access request", e);
        }
    }

    /**
     * Handle data portability request (GDPR Article 20)
     */
    @Transactional
    public DataPortabilityResponse handlePortabilityRequest(String userId, DataPortabilityRequest request) {
        log.info("Processing portability request for user: {}", userId);

        UserDataPackage dataPackage = collectUserData(userId);
        
        // Convert to machine-readable format
        PortableDataFormat portableData = convertToPortableFormat(dataPackage);
        
        if (request.getDirectTransfer() && request.getTargetController() != null) {
            // Direct transfer to another controller
            String transferId = initiateDirectTransfer(
                portableData, 
                request.getTargetController()
            );
            
            return DataPortabilityResponse.builder()
                    .transferId(transferId)
                    .status("TRANSFER_INITIATED")
                    .targetController(request.getTargetController())
                    .build();
        } else {
            // Provide download link
            String downloadUrl = dataExportService.generatePortableDataLink(
                userId, 
                portableData
            );
            
            return DataPortabilityResponse.builder()
                    .downloadUrl(downloadUrl)
                    .format("JSON")
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .build();
        }
    }

    /**
     * Handle rectification request (GDPR Article 16)
     */
    @Transactional
    public RectificationResponse handleRectificationRequest(String userId, RectificationRequest request) {
        log.info("Processing rectification request for user: {}", userId);

        // Validate requested changes
        validateRectificationRequest(request);

        // Apply changes
        Map<String, Object> changes = new HashMap<>();
        for (DataCorrection correction : request.getCorrections()) {
            Object oldValue = getUserDataField(userId, correction.getFieldPath());
            updateUserDataField(userId, correction.getFieldPath(), correction.getNewValue());
            changes.put(correction.getFieldPath(), Map.of(
                "old", oldValue,
                "new", correction.getNewValue()
            ));
        }

        // Notify downstream systems
        propagateDataChanges(userId, changes);

        // Audit the changes
        auditService.logDataModification(userId, "GDPR_RECTIFICATION", changes);

        return RectificationResponse.builder()
                .requestId(UUID.randomUUID().toString())
                .status("COMPLETED")
                .appliedChanges(changes)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Handle erasure request (GDPR Article 17 - Right to be forgotten)
     * Enhanced with comprehensive erasure tracking and certificate generation
     */
    @Transactional
    public ErasureResponse handleErasureRequest(String userId, ErasureRequest request) {
        log.info("Processing GDPR Article 17 erasure request for user: {}", userId);
        
        // Enhanced identity verification for erasure
        verifyIdentityWithEnhancedSecurity(userId, request.getVerificationData());
        
        // Create erasure request record
        DataRequest dataRequest = DataRequest.builder()
                .userId(userId)
                .type(RequestType.ERASURE)
                .status(RequestStatus.PROCESSING)
                .requestedAt(LocalDateTime.now())
                .deadline(LocalDateTime.now().plusDays(gdprResponseDays))
                .build();
        dataRequestRepository.save(dataRequest);

        // Check for legal obligations to retain data
        RetentionCheck retentionCheck = checkComprehensiveRetentionRequirements(userId);
        
        if (retentionCheck.hasRetentionRequirement()) {
            log.info("Cannot fully erase due to legal obligations: {}", retentionCheck.getObligations());
            
            // Perform partial erasure where legally permissible
            PartialErasureResult partialResult = performPartialErasure(userId, retentionCheck);
            
            // Apply pseudonymization to retained data
            applyPseudonymization(userId, retentionCheck.getRetainedCategories());
            
            return ErasureResponse.builder()
                    .requestId(dataRequest.getId())
                    .status("PARTIAL_ERASURE")
                    .reason(retentionCheck.getReason())
                    .retainedDataCategories(retentionCheck.getRetainedCategories())
                    .erasedCategories(partialResult.getErasedCategories())
                    .retentionReasons(retentionCheck.getObligations())
                    .erasureDate(retentionCheck.getErasureDate())
                    .alternativeAction("Pseudonymization applied to retained data")
                    .build();
        }

        // Full erasure process with transaction support
        ErasureTransaction erasureTransaction = ErasureTransaction.builder()
                .userId(userId)
                .requestId(dataRequest.getId())
                .startedAt(LocalDateTime.now())
                .systems(new ArrayList<>())
                .build();
        
        try {
            // Phase 1: Suspend all processing
            suspendAllProcessing(userId);
            
            // Phase 2: Create compliance backup (encrypted, time-limited)
            String backupId = createComplianceBackup(userId);
            erasureTransaction.setComplianceBackupId(backupId);
            
            // Phase 3: Perform erasure based on request type
            if (request.isFullErasure()) {
                performFullErasure(userId, erasureTransaction);
            } else {
                performSelectiveErasure(userId, request.getDataCategories(), erasureTransaction);
            }
            
            // Phase 4: Erase from all integrated systems
            eraseFromIntegratedSystems(userId, erasureTransaction);
            
            // Phase 5: Clear all caches
            clearAllCaches(userId);
            
            // Phase 6: Notify third parties of erasure obligation
            List<String> notifiedParties = notifyThirdPartiesOfErasure(userId);
            erasureTransaction.setNotifiedParties(notifiedParties);
            
            // Phase 7: Add to suppression list
            addToSuppressionList(userId);
            
            // Phase 8: Schedule backup deletion
            scheduleBackupDeletion(backupId, 90); // 90 days retention for compliance
            
            erasureTransaction.setCompletedAt(LocalDateTime.now());
            erasureTransaction.setStatus(ErasureStatus.COMPLETE);
            
            // Generate erasure certificate
            ErasureCertificate certificate = generateErasureCertificate(erasureTransaction);
            
            // Comprehensive audit with proof of erasure
            auditGDPRActivity(userId, "GDPR_ARTICLE_17_ERASURE_COMPLETED", certificate);
            
            // Publish event
            publishGDPREvent("ERASURE_COMPLETED", userId, dataRequest.getId());

            return ErasureResponse.builder()
                    .requestId(dataRequest.getId())
                    .status("COMPLETED")
                    .erasedCategories(request.getDataCategories())
                    .erasedSystems(erasureTransaction.getSystems())
                    .notifiedParties(notifiedParties)
                    .certificateId(certificate.getId())
                    .timestamp(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Erasure failed for user: {}", userId, e);
            rollbackErasure(erasureTransaction);
            throw new GDPRProcessingException("Erasure failed", e);
        }
    }

    /**
     * Handle consent management
     */
    @Transactional
    public ConsentResponse updateConsent(String userId, ConsentUpdateRequest request) {
        log.info("Updating consent for user: {}", userId);

        Consent consent = consentRepository.findByUserIdAndPurpose(userId, request.getPurpose())
                .orElse(new Consent());

        consent.setUserId(userId);
        consent.setPurpose(request.getPurpose());
        consent.setGranted(request.isGranted());
        consent.setVersion(request.getVersion());
        consent.setTimestamp(LocalDateTime.now());
        consent.setExpiresAt(calculateConsentExpiry(request.getPurpose()));

        consentRepository.save(consent);

        // Update processing based on consent
        updateProcessingBasedOnConsent(userId, request.getPurpose(), request.isGranted());

        return ConsentResponse.builder()
                .consentId(consent.getId())
                .purpose(consent.getPurpose())
                .granted(consent.isGranted())
                .expiresAt(consent.getExpiresAt())
                .build();
    }

    /**
     * Handle data restriction request (GDPR Article 18)
     */
    @Transactional
    public RestrictionResponse handleRestrictionRequest(String userId, RestrictionRequest request) {
        log.info("Processing restriction request for user: {}", userId);

        // Apply processing restrictions
        ProcessingRestriction restriction = ProcessingRestriction.builder()
                .userId(userId)
                .restrictedOperations(request.getOperations())
                .reason(request.getReason())
                .appliedAt(LocalDateTime.now())
                .expiresAt(request.getExpiryDate())
                .build();

        applyProcessingRestrictions(restriction);

        return RestrictionResponse.builder()
                .restrictionId(restriction.getId())
                .status("APPLIED")
                .restrictedOperations(restriction.getRestrictedOperations())
                .expiresAt(restriction.getExpiresAt())
                .build();
    }

    /**
     * Automated data retention management
     */
    @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    public void enforceDataRetention() {
        log.info("Running automated data retention enforcement");

        // Find data past retention period
        List<UserData> expiredData = userDataRepository.findDataPastRetention(
            LocalDateTime.now().minusDays(DATA_RETENTION_DAYS)
        );

        for (UserData data : expiredData) {
            try {
                // Check if there's a legal hold
                if (!hasLegalHold(data.getUserId())) {
                    // Anonymize or delete based on data type
                    if (data.isFinancialData()) {
                        anonymizeData(data);
                    } else {
                        deleteData(data);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing retention for user {}: {}", 
                    data.getUserId(), e.getMessage());
            }
        }
    }

    /**
     * Privacy by design implementation
     */
    public void implementPrivacyByDesign(String feature, PrivacyRequirements requirements) {
        // Data minimization
        enforceDataMinimization(feature, requirements.getRequiredFields());
        
        // Purpose limitation
        enforcePurposeLimitation(feature, requirements.getPurposes());
        
        // Storage limitation
        configureStorageLimitation(feature, requirements.getRetentionPeriod());
        
        // Security measures
        applySecurityMeasures(feature, requirements.getSecurityLevel());
    }

    /**
     * Generate GDPR compliance report
     */
    public ComplianceReport generateComplianceReport(LocalDateTime startDate, LocalDateTime endDate) {
        return ComplianceReport.builder()
                .period(startDate.toString() + " to " + endDate.toString())
                .dataRequests(getDataRequestStats(startDate, endDate))
                .consentMetrics(getConsentMetrics(startDate, endDate))
                .breachIncidents(getBreachIncidents(startDate, endDate))
                .retentionCompliance(getRetentionComplianceMetrics())
                .thirdPartyProcessors(getThirdPartyProcessorList())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Data breach notification (GDPR Article 33 & 34)
     */
    @Transactional
    public void handleDataBreach(DataBreachIncident incident) {
        log.error("Data breach detected: {}", incident.getDescription());

        // Assess the breach
        BreachAssessment assessment = assessBreach(incident);
        
        // Notify supervisory authority within 72 hours
        if (assessment.requiresAuthorityNotification()) {
            notifySupervisoryAuthority(incident, assessment);
        }
        
        // Notify affected individuals if high risk
        if (assessment.isHighRisk()) {
            notifyAffectedIndividuals(incident.getAffectedUsers(), incident);
        }
        
        // Document the breach
        documentBreach(incident, assessment);
        
        // Implement containment measures
        implementContainmentMeasures(incident);
    }

    // Helper methods

    private UserDataPackage collectUserData(String userId) {
        UserDataPackage dataPackage = new UserDataPackage();
        
        // Personal data
        dataPackage.addCategory("personal", getUserPersonalData(userId));
        
        // Transaction data
        dataPackage.addCategory("transactions", getUserTransactions(userId));
        
        // Activity logs
        dataPackage.addCategory("activity", getUserActivityLogs(userId));
        
        // Preferences and settings
        dataPackage.addCategory("preferences", getUserPreferences(userId));
        
        // Consents
        dataPackage.addCategory("consents", getUserConsents(userId));
        
        return dataPackage;
    }

    private void performFullErasure(String userId) {
        // Delete from all systems
        userDataRepository.deleteByUserId(userId);
        
        // Cascade to related services
        cascadeErasureToServices(userId);
        
        // Clear caches
        clearUserCaches(userId);
        
        // Archive for legal requirements (encrypted and access-restricted)
        archiveForLegalCompliance(userId);
    }

    private void anonymizeData(UserData data) {
        // Replace identifiable information with anonymized values
        data.setEmail(anonymizationService.anonymizeEmail(data.getEmail()));
        data.setName(anonymizationService.anonymizeName(data.getName()));
        data.setPhoneNumber(anonymizationService.anonymizePhone(data.getPhoneNumber()));
        data.setAnonymized(true);
        data.setAnonymizedAt(LocalDateTime.now());
        
        userDataRepository.save(data);
    }

    private RetentionCheck checkRetentionRequirements(String userId) {
        RetentionCheck check = new RetentionCheck();
        
        // Check for ongoing legal proceedings
        if (hasLegalProceedings(userId)) {
            check.addRequirement("Legal proceedings", Arrays.asList("transactions", "communications"));
        }
        
        // Check for tax obligations (7 years)
        if (hasRecentFinancialActivity(userId)) {
            check.addRequirement("Tax obligations", Arrays.asList("financial", "transactions"));
            check.setErasureDate(LocalDateTime.now().plusYears(7));
        }
        
        // Check for contractual obligations
        if (hasActiveContracts(userId)) {
            check.addRequirement("Active contracts", Arrays.asList("contracts", "agreements"));
        }
        
        return check;
    }

    private BreachAssessment assessBreach(DataBreachIncident incident) {
        BreachAssessment assessment = new BreachAssessment();
        
        // Evaluate likelihood and severity of risk
        assessment.setLikelihood(calculateRiskLikelihood(incident));
        assessment.setSeverity(calculateRiskSeverity(incident));
        
        // Determine if encryption was compromised
        assessment.setEncryptionCompromised(incident.isEncryptionCompromised());
        
        // Calculate overall risk level
        assessment.setRiskLevel(calculateOverallRisk(assessment));
        
        return assessment;
    }
    
    /**
     * GDPR Article 21: Right to Object
     * Handles objections to processing
     */
    @Transactional
    public ObjectionResponse handleObjectionRequest(String userId, ObjectionRequest request) {
        log.info("Processing GDPR Article 21 objection request for user: {}", userId);
        
        DataRequest dataRequest = createDataSubjectRequest(userId, RequestType.OBJECTION, request);
        
        // Evaluate objection grounds
        ObjectionAssessment assessment = evaluateObjection(userId, request);
        
        if (assessment.isValid()) {
            // Stop the objected processing
            stopProcessingActivities(userId, request.getObjectedProcessing());
            
            // Update consent records
            updateConsentRecords(userId, request.getObjectedProcessing());
            
            // Apply suppression
            applyProcessingSuppression(userId, request.getObjectedProcessing());
            
            auditGDPRActivity(userId, "GDPR_ARTICLE_21_OBJECTION_ACCEPTED", request);
            
            return ObjectionResponse.builder()
                .requestId(dataRequest.getId())
                .status(ObjectionStatus.ACCEPTED)
                .stoppedProcessing(request.getObjectedProcessing())
                .effectiveDate(LocalDateTime.now())
                .build();
        } else {
            // Document compelling legitimate grounds
            auditGDPRActivity(userId, "GDPR_ARTICLE_21_OBJECTION_OVERRIDDEN", assessment);
            
            return ObjectionResponse.builder()
                .requestId(dataRequest.getId())
                .status(ObjectionStatus.OVERRIDDEN)
                .legitimateGrounds(assessment.getCompellingGrounds())
                .explanation(assessment.getExplanation())
                .appealProcess(getAppealInformation())
                .build();
        }
    }
    
    /**
     * GDPR Article 22: Automated Decision Making
     */
    @Transactional
    public AutomatedDecisionResponse handleAutomatedDecisionRequest(String userId, AutomatedDecisionRequest request) {
        log.info("Processing GDPR Article 22 automated decision request for user: {}", userId);
        
        DataRequest dataRequest = createDataSubjectRequest(userId, RequestType.AUTOMATED_DECISION, request);
        
        switch (request.getAction()) {
            case EXPLANATION:
                DecisionExplanation explanation = explainAutomatedDecision(userId, request.getDecisionId());
                return AutomatedDecisionResponse.builder()
                    .requestId(dataRequest.getId())
                    .explanation(explanation)
                    .build();
                    
            case HUMAN_REVIEW:
                HumanReviewResult review = initiateHumanReview(userId, request.getDecisionId());
                return AutomatedDecisionResponse.builder()
                    .requestId(dataRequest.getId())
                    .reviewResult(review)
                    .build();
                    
            case OPT_OUT:
                optOutOfAutomatedDecisions(userId, request.getCategories());
                return AutomatedDecisionResponse.builder()
                    .requestId(dataRequest.getId())
                    .optedOutCategories(request.getCategories())
                    .build();
                    
            default:
                log.error("Unknown automated decision action requested: {} for user: {}", 
                    request.getAction(), userId);
                
                // Create audit entry for unknown action attempt
                auditService.logSecurityEvent(userId, "GDPR_UNKNOWN_ACTION", 
                    Map.of("action", request.getAction().toString(), 
                           "requestId", dataRequest.getId()));
                
                // Return error response instead of throwing exception
                return AutomatedDecisionResponse.builder()
                    .requestId(dataRequest.getId())
                    .errorCode("UNSUPPORTED_ACTION")
                    .errorMessage("The requested action '" + request.getAction() + 
                                "' is not supported. Supported actions are: EXPLANATION, HUMAN_REVIEW, OPT_OUT")
                    .supportedActions(Arrays.asList("EXPLANATION", "HUMAN_REVIEW", "OPT_OUT"))
                    .build();
        }
    }
    
    // Enhanced helper methods
    
    private void verifyIdentityWithMFA(String userId, Map<String, String> verificationData) {
        if (!performMFAVerification(userId, verificationData)) {
            throw new IdentityVerificationException("MFA verification failed");
        }
    }
    
    private void verifyIdentityWithEnhancedSecurity(String userId, Map<String, String> verificationData) {
        if (!performEnhancedVerification(userId, verificationData)) {
            throw new IdentityVerificationException("Enhanced verification failed");
        }
    }
    
    private UserDataPackage collectComprehensiveUserData(String userId) {
        UserDataPackage dataPackage = collectUserData(userId);
        
        // Add derived and inferred data
        dataPackage.addCategory("derived", getDerivedData(userId));
        dataPackage.addCategory("inferred", getInferredData(userId));
        
        // Add third-party data
        dataPackage.addCategory("thirdParty", getThirdPartyData(userId));
        
        return dataPackage;
    }
    
    private void auditGDPRActivity(String userId, String activity, Object details) {
        GDPRAuditLog auditLog = GDPRAuditLog.builder()
            .userId(userId)
            .activity(activity)
            .details(details)
            .timestamp(LocalDateTime.now())
            .ipAddress(getClientIpAddress())
            .userAgent(getClientUserAgent())
            .build();
            
        auditService.logGDPRActivity(auditLog);
    }
    
    private void publishGDPREvent(String eventType, String userId, String requestId) {
        GDPREvent event = GDPREvent.builder()
            .eventType(eventType)
            .userId(userId)
            .requestId(requestId)
            .timestamp(LocalDateTime.now())
            .build();
            
        kafkaTemplate.send("gdpr-events", event);
    }
    
    private void notifyDataSubject(String userId, String subject, String content) {
        notificationService.sendSecureNotification(userId, subject, content);
    }
    
    private String encryptDataPackage(UserDataPackage dataPackage, String userId) {
        return encryptionService.encryptWithUserKey(dataPackage, userId, encryptionAlgorithm);
    }
    
    private String calculateChecksum(String data) {
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(data);
    }
    
    /**
     * Monitor request deadlines
     */
    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
    public void monitorRequestDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        
        requestDeadlines.forEach((requestId, deadline) -> {
            long daysUntilDeadline = ChronoUnit.DAYS.between(now, deadline);
            
            if (daysUntilDeadline <= 3 && daysUntilDeadline > 0) {
                log.warn("GDPR request {} approaching deadline: {} days remaining", requestId, daysUntilDeadline);
                sendDeadlineWarning(requestId, daysUntilDeadline);
            } else if (daysUntilDeadline <= 0) {
                log.error("GDPR request {} has exceeded deadline!", requestId);
                escalateOverdueRequest(requestId);
            }
        });
    }
    
    private void sendDeadlineWarning(String requestId, long daysRemaining) {
        // Send warning notification to responsible team
        notificationService.sendInternalAlert(
            "GDPR Request Deadline Warning",
            String.format("Request %s has %d days until deadline", requestId, daysRemaining)
        );
    }
    
    private void escalateOverdueRequest(String requestId) {
        // Escalate to management and legal team
        notificationService.sendUrgentEscalation(
            "GDPR Request Deadline Exceeded",
            String.format("Request %s has exceeded the legal deadline!", requestId)
        );
    }
}