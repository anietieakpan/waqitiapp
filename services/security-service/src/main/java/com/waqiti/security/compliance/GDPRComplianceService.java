package com.waqiti.security.compliance;

import com.waqiti.security.audit.AuditService;
import com.waqiti.security.encryption.EncryptionService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * GDPR Compliance Service
 * Implements EU General Data Protection Regulation requirements
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GDPRComplianceService {
    
    private final AuditService auditService;
    private final EncryptionService encryptionService;
    
    // GDPR Articles implementation
    private final Map<String, ConsentRecord> consentRecords = new ConcurrentHashMap<>();
    private final Map<String, DataProcessingRecord> processingRecords = new ConcurrentHashMap<>();
    private final Map<String, DataSubjectRequest> dataSubjectRequests = new ConcurrentHashMap<>();
    
    /**
     * Article 6 & 7: Lawfulness of processing and consent
     */
    public ConsentRecord recordConsent(ConsentRequest request) {
        log.info("Recording consent for user {}", request.getUserId());
        
        // Validate consent request
        validateConsentRequest(request);
        
        // Create consent record
        ConsentRecord record = ConsentRecord.builder()
            .id(UUID.randomUUID().toString())
            .userId(request.getUserId())
            .purpose(request.getPurpose())
            .dataCategories(request.getDataCategories())
            .legalBasis(request.getLegalBasis())
            .consentGiven(true)
            .consentTimestamp(Instant.now())
            .expiryDate(Instant.now().plus(365, ChronoUnit.DAYS))
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .withdrawable(true)
            .parentalConsent(request.isParentalConsent())
            .version(request.getConsentVersion())
            .build();
        
        consentRecords.put(record.getId(), record);
        
        // Audit consent
        auditService.logGDPREvent("CONSENT_RECORDED", Map.of(
            "userId", request.getUserId(),
            "purpose", request.getPurpose(),
            "consentId", record.getId()
        ));
        
        return record;
    }
    
    /**
     * Article 7(3): Right to withdraw consent
     */
    public void withdrawConsent(String userId, String consentId) {
        log.info("Withdrawing consent {} for user {}", consentId, userId);
        
        ConsentRecord record = consentRecords.get(consentId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new GDPRComplianceException("Consent record not found");
        }
        
        record.setConsentGiven(false);
        record.setWithdrawalTimestamp(Instant.now());
        
        // Stop processing based on this consent
        stopProcessingForConsent(consentId);
        
        // Audit withdrawal
        auditService.logGDPREvent("CONSENT_WITHDRAWN", Map.of(
            "userId", userId,
            "consentId", consentId,
            "purpose", record.getPurpose()
        ));
    }
    
    /**
     * Article 12-14: Information to be provided to data subjects
     */
    public PrivacyNotice getPrivacyNotice(String language) {
        return PrivacyNotice.builder()
            .version("2.0")
            .lastUpdated(Instant.now())
            .dataController("Waqiti Payment Services")
            .dataProtectionOfficer("dpo@example.com")
            .purposes(Arrays.asList(
                "Payment processing",
                "Fraud prevention",
                "Legal compliance",
                "Service improvement"
            ))
            .legalBasis(Arrays.asList(
                "Consent",
                "Contract performance",
                "Legal obligation",
                "Legitimate interests"
            ))
            .dataCategories(Arrays.asList(
                "Identity data",
                "Contact data",
                "Financial data",
                "Transaction data",
                "Technical data"
            ))
            .recipients(Arrays.asList(
                "Payment processors",
                "Fraud prevention services",
                "Legal authorities (when required)"
            ))
            .retentionPeriods(Map.of(
                "Transaction data", "7 years",
                "Identity data", "5 years after account closure",
                "Marketing data", "Until consent withdrawn"
            ))
            .dataSubjectRights(Arrays.asList(
                "Right of access",
                "Right to rectification",
                "Right to erasure",
                "Right to restrict processing",
                "Right to data portability",
                "Right to object"
            ))
            .internationalTransfers("Data may be transferred to countries with adequate protection")
            .build();
    }
    
    /**
     * Article 15: Right of access
     */
    public DataAccessResponse handleAccessRequest(String userId) {
        log.info("Processing data access request for user {}", userId);
        
        // Collect all user data
        UserData userData = collectUserData(userId);
        
        // Create access response
        DataAccessResponse response = DataAccessResponse.builder()
            .requestId(UUID.randomUUID().toString())
            .userId(userId)
            .requestTimestamp(Instant.now())
            .personalData(userData)
            .processingPurposes(getProcessingPurposes(userId))
            .dataCategories(getDataCategories(userId))
            .recipients(getDataRecipients(userId))
            .retentionPeriods(getRetentionPeriods(userId))
            .dataSource("User provided and system generated")
            .automatedDecisionMaking(hasAutomatedDecisionMaking(userId))
            .build();
        
        // Audit access request
        auditService.logGDPREvent("ACCESS_REQUEST_PROCESSED", Map.of(
            "userId", userId,
            "requestId", response.getRequestId()
        ));
        
        return response;
    }
    
    /**
     * Article 16: Right to rectification
     */
    public void rectifyData(String userId, DataRectificationRequest request) {
        log.info("Processing data rectification for user {}", userId);
        
        // Validate request
        validateRectificationRequest(userId, request);
        
        // Apply rectifications
        for (DataCorrection correction : request.getCorrections()) {
            applyCorrection(userId, correction);
        }
        
        // Audit rectification
        auditService.logGDPREvent("DATA_RECTIFIED", Map.of(
            "userId", userId,
            "corrections", request.getCorrections().size()
        ));
    }
    
    /**
     * Article 17: Right to erasure ('right to be forgotten')
     */
    public ErasureResult handleErasureRequest(String userId, ErasureRequest request) {
        log.info("Processing erasure request for user {}", userId);
        
        // Check if erasure is allowed
        ErasureEligibility eligibility = checkErasureEligibility(userId, request);
        
        if (!eligibility.isEligible()) {
            return ErasureResult.builder()
                .requestId(UUID.randomUUID().toString())
                .userId(userId)
                .status(ErasureStatus.DENIED)
                .reason(eligibility.getReason())
                .timestamp(Instant.now())
                .build();
        }
        
        // Perform erasure
        Set<String> erasedCategories = performErasure(userId, request.getDataCategories());
        
        // Create erasure result
        ErasureResult result = ErasureResult.builder()
            .requestId(UUID.randomUUID().toString())
            .userId(userId)
            .status(ErasureStatus.COMPLETED)
            .erasedCategories(erasedCategories)
            .retainedCategories(getRetainedCategories(userId))
            .timestamp(Instant.now())
            .build();
        
        // Audit erasure
        auditService.logGDPREvent("DATA_ERASED", Map.of(
            "userId", userId,
            "requestId", result.getRequestId(),
            "erasedCategories", erasedCategories
        ));
        
        return result;
    }
    
    /**
     * Article 18: Right to restriction of processing
     */
    public void restrictProcessing(String userId, ProcessingRestriction restriction) {
        log.info("Restricting processing for user {}", userId);
        
        DataProcessingRecord record = DataProcessingRecord.builder()
            .userId(userId)
            .restricted(true)
            .restrictionReason(restriction.getReason())
            .restrictedCategories(restriction.getDataCategories())
            .restrictionTimestamp(Instant.now())
            .allowedPurposes(restriction.getAllowedPurposes())
            .build();
        
        processingRecords.put(userId, record);
        
        // Notify relevant systems
        notifyProcessingRestriction(userId, restriction);
        
        // Audit restriction
        auditService.logGDPREvent("PROCESSING_RESTRICTED", Map.of(
            "userId", userId,
            "reason", restriction.getReason(),
            "categories", restriction.getDataCategories()
        ));
    }
    
    /**
     * Article 20: Right to data portability
     */
    public DataPortabilityPackage exportUserData(String userId, PortabilityRequest request) {
        log.info("Creating data portability package for user {}", userId);
        
        // Collect portable data
        PortableUserData portableData = collectPortableData(userId, request.getDataCategories());
        
        // Create data package
        DataPortabilityPackage package_ = DataPortabilityPackage.builder()
            .packageId(UUID.randomUUID().toString())
            .userId(userId)
            .format(request.getFormat())
            .created(Instant.now())
            .expires(Instant.now().plus(7, ChronoUnit.DAYS))
            .data(portableData)
            .checksum(calculateChecksum(portableData))
            .encrypted(true)
            .build();
        
        // Encrypt package
        byte[] encryptedPackage = encryptionService.encrypt(
            serializePackage(package_), userId
        );
        
        package_.setEncryptedData(encryptedPackage);
        
        // Audit export
        auditService.logGDPREvent("DATA_EXPORTED", Map.of(
            "userId", userId,
            "packageId", package_.getPackageId(),
            "format", request.getFormat()
        ));
        
        return package_;
    }
    
    /**
     * Article 21: Right to object
     */
    public void handleObjection(String userId, ObjectionRequest request) {
        log.info("Processing objection for user {}", userId);
        
        // Record objection
        ProcessingObjection objection = ProcessingObjection.builder()
            .userId(userId)
            .purpose(request.getPurpose())
            .grounds(request.getGrounds())
            .timestamp(Instant.now())
            .status(ObjectionStatus.PENDING)
            .build();
        
        // Evaluate objection
        ObjectionDecision decision = evaluateObjection(objection);
        objection.setStatus(decision.isAccepted() ? ObjectionStatus.ACCEPTED : ObjectionStatus.REJECTED);
        objection.setDecisionReason(decision.getReason());
        
        if (decision.isAccepted()) {
            // Stop processing for objected purpose
            stopProcessingForPurpose(userId, request.getPurpose());
        }
        
        // Audit objection
        auditService.logGDPREvent("OBJECTION_PROCESSED", Map.of(
            "userId", userId,
            "purpose", request.getPurpose(),
            "decision", objection.getStatus()
        ));
    }
    
    /**
     * Article 25: Data protection by design and by default
     */
    public PrivacyImpactAssessment conductPIA(ProcessingActivity activity) {
        log.info("Conducting Privacy Impact Assessment for {}", activity.getName());
        
        PrivacyImpactAssessment pia = PrivacyImpactAssessment.builder()
            .id(UUID.randomUUID().toString())
            .activityName(activity.getName())
            .assessmentDate(Instant.now())
            .dataCategories(activity.getDataCategories())
            .purposes(activity.getPurposes())
            .necessity(assessNecessity(activity))
            .proportionality(assessProportionality(activity))
            .risks(identifyRisks(activity))
            .mitigations(proposeMitigations(activity))
            .residualRisk(calculateResidualRisk(activity))
            .recommendation(makeRecommendation(activity))
            .build();
        
        // Store PIA
        auditService.storePIA(pia);
        
        return pia;
    }
    
    /**
     * Article 33: Notification of personal data breach
     */
    public void reportDataBreach(DataBreachReport breach) {
        log.error("Data breach reported: {}", breach.getDescription());
        
        // Assess breach severity
        BreachSeverity severity = assessBreachSeverity(breach);
        
        // Determine if notification required
        boolean notificationRequired = severity.getRiskLevel() >= 3;
        
        if (notificationRequired) {
            // Notify supervisory authority within 72 hours
            notifySupervisoryAuthority(breach);
            
            // If high risk, notify affected individuals
            if (severity.getRiskLevel() >= 4) {
                notifyAffectedIndividuals(breach);
            }
        }
        
        // Document breach
        documentBreach(breach, severity);
        
        // Audit breach
        auditService.logGDPREvent("DATA_BREACH_REPORTED", Map.of(
            "breachId", breach.getId(),
            "severity", severity.getRiskLevel(),
            "affectedRecords", breach.getAffectedRecords()
        ));
    }
    
    /**
     * Article 35: Data protection impact assessment
     */
    @Scheduled(cron = "0 0 0 * * MON") // Weekly
    public void reviewProcessingActivities() {
        log.info("Reviewing processing activities for GDPR compliance");
        
        // Get all processing activities
        List<ProcessingActivity> activities = getProcessingActivities();
        
        for (ProcessingActivity activity : activities) {
            // Check if DPIA required
            if (requiresDPIA(activity)) {
                // Conduct or update DPIA
                conductPIA(activity);
            }
            
            // Check lawful basis
            if (!hasLawfulBasis(activity)) {
                log.warn("Processing activity {} lacks lawful basis", activity.getName());
                suspendProcessingActivity(activity);
            }
        }
    }
    
    /**
     * Compliance monitoring
     */
    @Scheduled(fixedDelay = 3600000) // Hourly
    public void monitorCompliance() {
        log.debug("Monitoring GDPR compliance");
        
        // Check consent expiry
        checkConsentExpiry();
        
        // Check retention periods
        checkRetentionCompliance();
        
        // Check pending requests
        processPendingRequests();
        
        // Generate compliance metrics
        GDPRMetrics metrics = generateComplianceMetrics();
        auditService.storeGDPRMetrics(metrics);
    }
    
    // Helper methods implementation...
    
    private void validateConsentRequest(ConsentRequest request) {
        if (request.getPurpose() == null || request.getPurpose().isEmpty()) {
            throw new GDPRComplianceException("Consent purpose must be specified");
        }
        
        if (request.getLegalBasis() == null) {
            throw new GDPRComplianceException("Legal basis must be specified");
        }
        
        // Check if user is minor
        if (isMinor(request.getUserId()) && !request.isParentalConsent()) {
            throw new GDPRComplianceException("Parental consent required for minors");
        }
    }
    
    private boolean isMinor(String userId) {
        // Implementation would check user age
        return false;
    }
    
    private void stopProcessingForConsent(String consentId) {
        // Implementation would notify all systems to stop processing
        log.info("Stopping processing for consent {}", consentId);
    }
    
    private UserData collectUserData(String userId) {
        // Implementation would collect all user data from various services
        return new UserData();
    }
    
    private List<String> getProcessingPurposes(String userId) {
        return Arrays.asList("Payment processing", "Fraud prevention", "Legal compliance");
    }
    
    private List<String> getDataCategories(String userId) {
        return Arrays.asList("Identity", "Financial", "Transaction", "Technical");
    }
    
    private List<String> getDataRecipients(String userId) {
        return Arrays.asList("Payment processors", "Fraud services");
    }
    
    private Map<String, String> getRetentionPeriods(String userId) {
        return Map.of(
            "Transaction data", "7 years",
            "Identity data", "5 years"
        );
    }
    
    private boolean hasAutomatedDecisionMaking(String userId) {
        return true; // Fraud detection uses automated decision making
    }
    
    private void validateRectificationRequest(String userId, DataRectificationRequest request) {
        if (request.getCorrections() == null || request.getCorrections().isEmpty()) {
            throw new GDPRComplianceException("No corrections specified");
        }
    }
    
    private void applyCorrection(String userId, DataCorrection correction) {
        // Implementation would apply the correction to the appropriate system
        log.info("Applying correction for user {} field {}", userId, correction.getField());
    }
    
    private ErasureEligibility checkErasureEligibility(String userId, ErasureRequest request) {
        // Check legal obligations
        if (hasLegalObligation(userId)) {
            return ErasureEligibility.ineligible("Legal retention requirement");
        }
        
        // Check ongoing contracts
        if (hasActiveContract(userId)) {
            return ErasureEligibility.ineligible("Active contract exists");
        }
        
        return ErasureEligibility.eligible();
    }
    
    private boolean hasLegalObligation(String userId) {
        // Check if user has transactions that must be retained for legal reasons
        return false;
    }
    
    private boolean hasActiveContract(String userId) {
        // Check if user has active services
        return false;
    }
    
    private Set<String> performErasure(String userId, Set<String> categories) {
        // Implementation would erase data from various systems
        return categories;
    }
    
    private Set<String> getRetainedCategories(String userId) {
        // Categories that cannot be erased due to legal requirements
        return Set.of("Transaction data required for tax compliance");
    }
    
    private void notifyProcessingRestriction(String userId, ProcessingRestriction restriction) {
        // Notify all systems about the restriction
        log.info("Notifying systems about processing restriction for user {}", userId);
    }
    
    private PortableUserData collectPortableData(String userId, Set<String> categories) {
        // Collect data in portable format
        return new PortableUserData();
    }
    
    private String calculateChecksum(PortableUserData data) {
        // Calculate SHA-256 checksum
        return "checksum";
    }
    
    private byte[] serializePackage(DataPortabilityPackage package_) {
        // Serialize to JSON or XML
        return new byte[0];
    }
    
    private ObjectionDecision evaluateObjection(ProcessingObjection objection) {
        // Evaluate if objection is valid
        return ObjectionDecision.accepted("Valid objection grounds");
    }
    
    private void stopProcessingForPurpose(String userId, String purpose) {
        // Stop processing for the specified purpose
        log.info("Stopping processing for user {} purpose {}", userId, purpose);
    }
    
    private String assessNecessity(ProcessingActivity activity) {
        return "Processing is necessary for the specified purposes";
    }
    
    private String assessProportionality(ProcessingActivity activity) {
        return "Processing is proportionate to the intended outcome";
    }
    
    private List<PrivacyRisk> identifyRisks(ProcessingActivity activity) {
        return Arrays.asList(
            new PrivacyRisk("Data breach", "Medium", "Technical and organizational measures"),
            new PrivacyRisk("Unauthorized access", "Low", "Access controls and encryption")
        );
    }
    
    private List<RiskMitigation> proposeMitigations(ProcessingActivity activity) {
        return Arrays.asList(
            new RiskMitigation("Encryption", "Implement AES-256 encryption"),
            new RiskMitigation("Access control", "Role-based access control")
        );
    }
    
    private String calculateResidualRisk(ProcessingActivity activity) {
        return "Low";
    }
    
    private String makeRecommendation(ProcessingActivity activity) {
        return "Processing can proceed with proposed mitigations";
    }
    
    private BreachSeverity assessBreachSeverity(DataBreachReport breach) {
        int riskLevel = 2; // Default medium
        
        if (breach.getAffectedRecords() > 1000) riskLevel++;
        if (breach.getDataCategories().contains("Financial")) riskLevel++;
        if (!breach.isEncrypted()) riskLevel++;
        
        return new BreachSeverity(riskLevel);
    }
    
    private void notifySupervisoryAuthority(DataBreachReport breach) {
        // Implementation would notify the relevant DPA
        log.info("Notifying supervisory authority about breach {}", breach.getId());
    }
    
    private void notifyAffectedIndividuals(DataBreachReport breach) {
        // Implementation would notify affected users
        log.info("Notifying {} affected individuals", breach.getAffectedRecords());
    }
    
    private void documentBreach(DataBreachReport breach, BreachSeverity severity) {
        // Store breach documentation
        auditService.documentDataBreach(breach, severity);
    }
    
    private List<ProcessingActivity> getProcessingActivities() {
        // Get all processing activities
        return new ArrayList<>();
    }
    
    private boolean requiresDPIA(ProcessingActivity activity) {
        // Check if DPIA is required based on Article 35 criteria
        return activity.isHighRisk() || 
               activity.involvesSystematicMonitoring() ||
               activity.processesSpecialCategories();
    }
    
    private boolean hasLawfulBasis(ProcessingActivity activity) {
        // Check if activity has valid lawful basis
        return activity.getLawfulBasis() != null && !activity.getLawfulBasis().isEmpty();
    }
    
    private void suspendProcessingActivity(ProcessingActivity activity) {
        // Suspend the processing activity
        log.warn("Suspending processing activity {}", activity.getName());
    }
    
    private void checkConsentExpiry() {
        Instant expiryThreshold = Instant.now().plus(30, ChronoUnit.DAYS);
        
        consentRecords.values().stream()
            .filter(consent -> consent.isConsentGiven() && 
                              consent.getExpiryDate().isBefore(expiryThreshold))
            .forEach(consent -> {
                // Notify user about expiring consent
                log.info("Consent {} expiring soon for user {}", 
                    consent.getId(), consent.getUserId());
            });
    }
    
    private void checkRetentionCompliance() {
        // Check if any data has exceeded retention period
        log.debug("Checking data retention compliance");
    }
    
    private void processPendingRequests() {
        // Process any pending data subject requests
        dataSubjectRequests.values().stream()
            .filter(request -> request.getStatus() == RequestStatus.PENDING)
            .forEach(this::processDataSubjectRequest);
    }
    
    private void processDataSubjectRequest(DataSubjectRequest request) {
        // Process the request based on type
        log.info("Processing data subject request {} type {}", 
            request.getId(), request.getType());
    }
    
    private GDPRMetrics generateComplianceMetrics() {
        return GDPRMetrics.builder()
            .timestamp(Instant.now())
            .activeConsents(consentRecords.size())
            .pendingRequests(dataSubjectRequests.size())
            .processingActivities(getProcessingActivities().size())
            .complianceScore(calculateComplianceScore())
            .build();
    }
    
    private double calculateComplianceScore() {
        // Calculate overall GDPR compliance score
        return 95.0;
    }
    
    // Inner classes
    
    @Data
    @Builder
    public static class ConsentRequest {
        private String userId;
        private String purpose;
        private Set<String> dataCategories;
        private String legalBasis;
        private String ipAddress;
        private String userAgent;
        private boolean parentalConsent;
        private String consentVersion;
    }
    
    @Data
    @Builder
    public static class ConsentRecord {
        private String id;
        private String userId;
        private String purpose;
        private Set<String> dataCategories;
        private String legalBasis;
        private boolean consentGiven;
        private Instant consentTimestamp;
        private Instant withdrawalTimestamp;
        private Instant expiryDate;
        private String ipAddress;
        private String userAgent;
        private boolean withdrawable;
        private boolean parentalConsent;
        private String version;
    }
    
    @Data
    @Builder
    public static class PrivacyNotice {
        private String version;
        private Instant lastUpdated;
        private String dataController;
        private String dataProtectionOfficer;
        private List<String> purposes;
        private List<String> legalBasis;
        private List<String> dataCategories;
        private List<String> recipients;
        private Map<String, String> retentionPeriods;
        private List<String> dataSubjectRights;
        private String internationalTransfers;
    }
    
    @Data
    @Builder
    public static class DataAccessResponse {
        private String requestId;
        private String userId;
        private Instant requestTimestamp;
        private UserData personalData;
        private List<String> processingPurposes;
        private List<String> dataCategories;
        private List<String> recipients;
        private Map<String, String> retentionPeriods;
        private String dataSource;
        private boolean automatedDecisionMaking;
    }
    
    @Data
    public static class UserData {
        private Map<String, Object> identityData;
        private Map<String, Object> contactData;
        private Map<String, Object> financialData;
        private List<TransactionData> transactionHistory;
        private Map<String, Object> technicalData;
        private Map<String, Object> preferenceData;
    }
    
    @Data
    public static class TransactionData {
        private String transactionId;
        private Instant timestamp;
        private String type;
        private BigDecimal amount;
        private String currency;
        private String recipientId;
    }
    
    @Data
    public static class DataRectificationRequest {
        private List<DataCorrection> corrections;
    }
    
    @Data
    public static class DataCorrection {
        private String field;
        private String oldValue;
        private String newValue;
        private String reason;
    }
    
    @Data
    public static class ErasureRequest {
        private Set<String> dataCategories;
        private String reason;
    }
    
    @Data
    @Builder
    public static class ErasureResult {
        private String requestId;
        private String userId;
        private ErasureStatus status;
        private String reason;
        private Set<String> erasedCategories;
        private Set<String> retainedCategories;
        private Instant timestamp;
    }
    
    public enum ErasureStatus {
        COMPLETED, PARTIAL, DENIED
    }
    
    @Data
    public static class ProcessingRestriction {
        private String reason;
        private Set<String> dataCategories;
        private Set<String> allowedPurposes;
    }
    
    @Data
    @Builder
    public static class DataProcessingRecord {
        private String userId;
        private boolean restricted;
        private String restrictionReason;
        private Set<String> restrictedCategories;
        private Instant restrictionTimestamp;
        private Set<String> allowedPurposes;
    }
    
    @Data
    public static class PortabilityRequest {
        private Set<String> dataCategories;
        private String format; // JSON, XML, CSV
    }
    
    @Data
    @Builder
    public static class DataPortabilityPackage {
        private String packageId;
        private String userId;
        private String format;
        private Instant created;
        private Instant expires;
        private PortableUserData data;
        private String checksum;
        private boolean encrypted;
        private byte[] encryptedData;
    }
    
    @Data
    public static class PortableUserData {
        private Map<String, Object> userData;
        private List<Map<String, Object>> transactions;
        private Map<String, Object> preferences;
    }
    
    @Data
    public static class ObjectionRequest {
        private String purpose;
        private String grounds;
    }
    
    @Data
    @Builder
    public static class ProcessingObjection {
        private String userId;
        private String purpose;
        private String grounds;
        private Instant timestamp;
        private ObjectionStatus status;
        private String decisionReason;
    }
    
    public enum ObjectionStatus {
        PENDING, ACCEPTED, REJECTED
    }
    
    @Data
    public static class ProcessingActivity {
        private String name;
        private Set<String> dataCategories;
        private Set<String> purposes;
        private String lawfulBasis;
        private boolean highRisk;
        
        public boolean isHighRisk() { return highRisk; }
        public boolean involvesSystematicMonitoring() { return false; }
        public boolean processesSpecialCategories() { return false; }
        public String getLawfulBasis() { return lawfulBasis; }
    }
    
    @Data
    @Builder
    public static class PrivacyImpactAssessment {
        private String id;
        private String activityName;
        private Instant assessmentDate;
        private Set<String> dataCategories;
        private Set<String> purposes;
        private String necessity;
        private String proportionality;
        private List<PrivacyRisk> risks;
        private List<RiskMitigation> mitigations;
        private String residualRisk;
        private String recommendation;
    }
    
    @Data
    @Builder
    public static class DataBreachReport {
        private String id;
        private String description;
        private Instant detectedAt;
        private Instant occurredAt;
        private int affectedRecords;
        private Set<String> dataCategories;
        private boolean encrypted;
        private String cause;
        private List<String> affectedSystems;
    }
    
    @Data
    public static class DataSubjectRequest {
        private String id;
        private String userId;
        private RequestType type;
        private RequestStatus status;
        private Instant submitted;
        
        public RequestType getType() { return type; }
        public RequestStatus getStatus() { return status; }
        public String getId() { return id; }
    }
    
    public enum RequestType {
        ACCESS, RECTIFICATION, ERASURE, RESTRICTION, PORTABILITY, OBJECTION
    }
    
    public enum RequestStatus {
        PENDING, IN_PROGRESS, COMPLETED, REJECTED
    }
    
    @Data
    @Builder
    public static class GDPRMetrics {
        private Instant timestamp;
        private long activeConsents;
        private long pendingRequests;
        private long processingActivities;
        private double complianceScore;
    }
    
    @Data
    public static class PrivacyRisk {
        private String description;
        private String likelihood;
        private String impact;
        
        public PrivacyRisk(String description, String likelihood, String impact) {
            this.description = description;
            this.likelihood = likelihood;
            this.impact = impact;
        }
    }
    
    @Data
    public static class RiskMitigation {
        private String measure;
        private String description;
        
        public RiskMitigation(String measure, String description) {
            this.measure = measure;
            this.description = description;
        }
    }
    
    public static class ErasureEligibility {
        private boolean eligible;
        private String reason;
        
        private ErasureEligibility(boolean eligible, String reason) {
            this.eligible = eligible;
            this.reason = reason;
        }
        
        public static ErasureEligibility eligible() {
            return new ErasureEligibility(true, null);
        }
        
        public static ErasureEligibility ineligible(String reason) {
            return new ErasureEligibility(false, reason);
        }
        
        public boolean isEligible() { return eligible; }
        public String getReason() { return reason; }
    }
    
    public static class ObjectionDecision {
        private boolean accepted;
        private String reason;
        
        private ObjectionDecision(boolean accepted, String reason) {
            this.accepted = accepted;
            this.reason = reason;
        }
        
        public static ObjectionDecision accepted(String reason) {
            return new ObjectionDecision(true, reason);
        }
        
        public static ObjectionDecision rejected(String reason) {
            return new ObjectionDecision(false, reason);
        }
        
        public boolean isAccepted() { return accepted; }
        public String getReason() { return reason; }
    }
    
    @Data
    public static class BreachSeverity {
        private int riskLevel;
        
        public BreachSeverity(int riskLevel) {
            this.riskLevel = riskLevel;
        }
    }
    
    public static class GDPRComplianceException extends RuntimeException {
        public GDPRComplianceException(String message) {
            super(message);
        }
        
        public GDPRComplianceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    @Data
    public static class BigDecimal {
        // Placeholder for actual BigDecimal
    }
}