package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced Suspicious Activity Report (SAR) model for FinCEN compliance.
 * 
 * Implements comprehensive SAR filing requirements including:
 * - FinCEN Form 111 (Suspicious Activity Report by Money Services Business)
 * - FinCEN Form 109 (Suspicious Activity Report by Depository Institution)
 * - BSA E-Filing System compliance
 * - Multi-jurisdiction reporting support
 * 
 * Regulatory compliance:
 * - 31 CFR 1022.320 (Money Services Business)
 * - 31 CFR 1020.320 (Banks)
 * - BSA requirements
 * - FATF recommendations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousActivityReport {
    
    // Core identification
    @NotNull(message = "SAR ID is required")
    private UUID id;
    
    @NotBlank(message = "Report number is required")
    @Size(max = 50, message = "Report number cannot exceed 50 characters")
    private String reportNumber;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    private UUID transactionId;
    
    // SAR Type and Classification
    @NotNull(message = "SAR type is required")
    private SarType reportType;
    
    @NotNull(message = "SAR priority is required")
    private SarPriority priority;
    
    @NotNull(message = "SAR status is required")
    private SarStatus status;
    
    // Financial Information
    @NotNull(message = "Total amount is required")
    private BigDecimal totalAmount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    private String currency;
    
    private BigDecimal exchangeRate; // For non-USD transactions
    private BigDecimal usdEquivalent; // USD equivalent amount
    
    // Activity Description
    @NotBlank(message = "Suspicious activity description is required")
    @Size(max = 50000, message = "Suspicious activity description cannot exceed 50,000 characters")
    private String suspiciousActivity;
    
    @NotBlank(message = "Narrative description is required")
    @Size(max = 50000, message = "Narrative description cannot exceed 50,000 characters")
    private String narrativeDescription;
    
    // Institution Information
    @NotBlank(message = "Reporting institution is required")
    @Size(max = 200, message = "Reporting institution name cannot exceed 200 characters")
    private String reportingInstitution;
    
    @NotBlank(message = "Institution EIN is required")
    @Pattern(regexp = "^\\d{2}-\\d{7}$", message = "EIN must be in format XX-XXXXXXX")
    private String institutionEIN;
    
    @NotBlank(message = "Institution address is required")
    private String institutionAddress;
    
    @NotBlank(message = "Institution phone is required")
    private String institutionPhone;
    
    // Contact Information
    @NotBlank(message = "Contact name is required")
    private String contactName;
    
    @NotBlank(message = "Contact title is required")
    private String contactTitle;
    
    @NotBlank(message = "Contact phone is required")
    private String contactPhone;
    
    @NotBlank(message = "Contact email is required")
    private String contactEmail;
    
    // Dates and Timing
    @NotNull(message = "Filing date is required")
    private LocalDateTime filingDate;
    
    @NotNull(message = "Incident date is required")
    private LocalDateTime incidentDate;
    
    private LocalDateTime detectionDate; // When suspicious activity was first detected
    private LocalDateTime reportingDate; // When decision to report was made
    
    // Involved Parties
    @Builder.Default
    private List<String> involvedParties = new ArrayList<>();
    
    @Builder.Default
    private List<SarSubject> subjects = new ArrayList<>();
    
    @Builder.Default
    private List<String> supportingDocuments = new ArrayList<>();
    
    // Regulatory and Compliance
    @NotBlank(message = "Jurisdiction code is required")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Jurisdiction code must be a valid 2-letter country code")
    private String jurisdictionCode;
    
    @Builder.Default
    private Map<String, Object> regulatoryFlags = new HashMap<>();
    
    private String regulatoryAuthority; // FinCEN, FCA, AUSTRAC, etc.
    private String submissionReference; // Reference from regulatory authority
    private LocalDateTime submissionDeadline;
    
    // Review and Approval Workflow
    private UUID createdBy;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private UUID approvedBy;
    private LocalDateTime approvedAt;
    private String approvalNotes;
    
    // Submission Tracking
    private LocalDateTime submittedAt;
    private String submissionMethod; // BSA_E_FILING, MANUAL, API
    private String submissionStatus; // PENDING, ACCEPTED, REJECTED
    private String submissionError;
    
    // Follow-up and Monitoring
    private boolean requiresFollowUp;
    private LocalDateTime followUpDate;
    private String followUpReason;
    private UUID assignedTo;
    
    // Legal and Law Enforcement
    private boolean lawEnforcementNotified;
    private LocalDateTime lawEnforcementNotificationDate;
    private String lawEnforcementAgency;
    private String lawEnforcementReference;
    
    // Risk Assessment
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private BigDecimal riskScore; // 0-100
    private String threatCategory; // MONEY_LAUNDERING, TERRORIST_FINANCING, etc.
    
    // Geographic Information
    private String sourceCountry;
    private String destinationCountry;
    private boolean crossBorderTransaction;
    private List<String> transitCountries;
    
    // Technology and Method
    private String transactionMethod; // WIRE, ACH, CRYPTO, CASH, etc.
    private String deliveryMethod; // ELECTRONIC, PHYSICAL, etc.
    private boolean digitalTransaction;
    
    // Pattern and Behavior Analysis
    @Builder.Default
    private Map<String, Object> patternAnalysis = new HashMap<>();
    
    @Builder.Default
    private Map<String, Object> behaviorIndicators = new HashMap<>();
    
    // Quality Assurance
    private boolean qualityReviewed;
    private UUID qualityReviewedBy;
    private LocalDateTime qualityReviewDate;
    private String qualityNotes;
    
    // Audit and Metadata
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    private String auditTrailId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    
    // Statistical and Reporting
    private String reportingPeriod; // YYYY-MM format
    private int amendmentNumber; // 0 for original, 1+ for amendments
    private UUID originalSarId; // If this is an amendment
    
    /**
     * SAR Types based on FinCEN requirements
     */
    public enum SarType {
        STRUCTURING,
        MONEY_LAUNDERING,
        TERRORIST_FINANCING,
        FRAUD,
        IDENTITY_THEFT,
        CYBER_CRIME,
        ELDER_FINANCIAL_ABUSE,
        HUMAN_TRAFFICKING,
        DRUG_TRAFFICKING,
        SANCTIONS_EVASION,
        UNUSUAL_TRANSACTION,
        BULK_CASH_SMUGGLING,
        TRADE_BASED_MONEY_LAUNDERING,
        PREPAID_CARD_ABUSE,
        CRYPTOCURRENCY_SUSPICIOUS,
        OTHER
    }
    
    /**
     * SAR Priority levels for processing
     */
    public enum SarPriority {
        URGENT(1, "Immediate attention required - potential ongoing criminal activity"),
        HIGH(2, "High priority - significant suspicious activity"),
        NORMAL(3, "Standard processing priority"),
        LOW(4, "Low priority - monitoring purposes");
        
        private final int level;
        private final String description;
        
        SarPriority(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
    
    /**
     * SAR Status throughout lifecycle
     */
    public enum SarStatus {
        DRAFT,
        PENDING_REVIEW,
        UNDER_REVIEW,
        RETURNED_FOR_REVISION,
        PENDING_APPROVAL,
        APPROVED_FOR_SUBMISSION,
        SUBMITTED,
        ACKNOWLEDGED,
        FOLLOW_UP_REQUIRED,
        CLOSED,
        REJECTED,
        CANCELLED
    }
    
    /**
     * Subject information for SAR
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SarSubject {
        private String type; // INDIVIDUAL, ORGANIZATION, ACCOUNT
        private String firstName;
        private String lastName;
        private String organizationName;
        private String dateOfBirth;
        private String ssn;
        private String ein;
        private String address;
        private String phone;
        private String email;
        private String identification;
        private String identificationType;
        private String nationality;
        private String role; // SENDER, RECEIVER, BENEFICIARY, etc.
        private Map<String, Object> additionalInfo;
    }
    
    /**
     * Factory method for creating new SAR
     */
    public static SuspiciousActivityReport createNew(UUID userId, SarType reportType, 
                                                   BigDecimal amount, String currency) {
        return SuspiciousActivityReport.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .reportType(reportType)
                .priority(SarPriority.NORMAL)
                .status(SarStatus.DRAFT)
                .totalAmount(amount)
                .currency(currency)
                .filingDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .reportNumber(generateReportNumber())
                .regulatoryFlags(new HashMap<>())
                .metadata(new HashMap<>())
                .subjects(new ArrayList<>())
                .involvedParties(new ArrayList<>())
                .supportingDocuments(new ArrayList<>())
                .build();
    }
    
    /**
     * Generate unique report number
     */
    public static String generateReportNumber() {
        return "SAR-" + System.currentTimeMillis() + "-" + 
               UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Add a subject to the SAR
     */
    public void addSubject(SarSubject subject) {
        if (subjects == null) {
            subjects = new ArrayList<>();
        }
        subjects.add(subject);
    }
    
    /**
     * Add supporting document reference
     */
    public void addSupportingDocument(String documentReference) {
        if (supportingDocuments == null) {
            supportingDocuments = new ArrayList<>();
        }
        supportingDocuments.add(documentReference);
    }
    
    /**
     * Add regulatory flag
     */
    public void addRegulatoryFlag(String flag, Object value) {
        if (regulatoryFlags == null) {
            regulatoryFlags = new HashMap<>();
        }
        regulatoryFlags.put(flag, value);
    }
    
    /**
     * Calculate USD equivalent if needed
     */
    public void calculateUsdEquivalent() {
        if (exchangeRate != null && !"USD".equals(currency)) {
            this.usdEquivalent = totalAmount.multiply(exchangeRate);
        } else if ("USD".equals(currency)) {
            this.usdEquivalent = totalAmount;
        }
    }
    
    /**
     * Check if SAR is ready for submission
     */
    public boolean isReadyForSubmission() {
        return status == SarStatus.APPROVED_FOR_SUBMISSION &&
               suspiciousActivity != null && !suspiciousActivity.trim().isEmpty() &&
               narrativeDescription != null && !narrativeDescription.trim().isEmpty() &&
               totalAmount != null &&
               currency != null &&
               subjects != null && !subjects.isEmpty();
    }
    
    /**
     * Check if SAR requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return priority == SarPriority.URGENT ||
               (reportType == SarType.TERRORIST_FINANCING) ||
               (reportType == SarType.HUMAN_TRAFFICKING) ||
               (lawEnforcementNotified && lawEnforcementNotificationDate != null);
    }
    
    /**
     * Check if SAR is overdue
     */
    public boolean isOverdue() {
        if (submissionDeadline == null) {
            // Default 30-day deadline from incident date
            submissionDeadline = incidentDate.plusDays(30);
        }
        return LocalDateTime.now().isAfter(submissionDeadline) && 
               status != SarStatus.SUBMITTED && 
               status != SarStatus.CLOSED;
    }
    
    /**
     * Generate BSA E-Filing XML format
     */
    public String generateBSAXML() {
        // Implementation would generate actual BSA E-Filing XML
        // This is a placeholder for the structure
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<SAR>\n" +
               "  <ReportNumber>" + reportNumber + "</ReportNumber>\n" +
               "  <FilingDate>" + filingDate + "</FilingDate>\n" +
               "  <IncidentDate>" + incidentDate + "</IncidentDate>\n" +
               "  <Amount>" + totalAmount + "</Amount>\n" +
               "  <Currency>" + currency + "</Currency>\n" +
               "  <SuspiciousActivity><![CDATA[" + suspiciousActivity + "]]></SuspiciousActivity>\n" +
               "  <Narrative><![CDATA[" + narrativeDescription + "]]></Narrative>\n" +
               "</SAR>";
    }
    
    /**
     * Validate SAR for submission
     */
    public void validateForSubmission() {
        List<String> errors = new ArrayList<>();
        
        if (reportNumber == null || reportNumber.trim().isEmpty()) {
            errors.add("Report number is required");
        }
        
        if (suspiciousActivity == null || suspiciousActivity.trim().isEmpty()) {
            errors.add("Suspicious activity description is required");
        }
        
        if (narrativeDescription == null || narrativeDescription.trim().isEmpty()) {
            errors.add("Narrative description is required");
        }
        
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Valid total amount is required");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            errors.add("Currency is required");
        }
        
        if (reportingInstitution == null || reportingInstitution.trim().isEmpty()) {
            errors.add("Reporting institution is required");
        }
        
        if (contactName == null || contactName.trim().isEmpty()) {
            errors.add("Contact name is required");
        }
        
        if (subjects == null || subjects.isEmpty()) {
            errors.add("At least one subject is required");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalStateException("SAR validation failed: " + String.join(", ", errors));
        }
    }
    
    /**
     * Create audit summary
     */
    public String createAuditSummary() {
        return String.format(
            "SAR %s: Type=%s, Amount=%s %s, Status=%s, Priority=%s, Created=%s",
            reportNumber, reportType, totalAmount, currency, status, priority, createdAt
        );
    }
    
    /**
     * Clone SAR for amendment
     */
    public SuspiciousActivityReport createAmendment() {
        SuspiciousActivityReport amendment = SuspiciousActivityReport.builder()
                .id(UUID.randomUUID())
                .originalSarId(this.id)
                .amendmentNumber(this.amendmentNumber + 1)
                .userId(this.userId)
                .transactionId(this.transactionId)
                .reportType(this.reportType)
                .priority(this.priority)
                .status(SarStatus.DRAFT)
                .totalAmount(this.totalAmount)
                .currency(this.currency)
                .suspiciousActivity(this.suspiciousActivity)
                .narrativeDescription(this.narrativeDescription)
                .reportingInstitution(this.reportingInstitution)
                .institutionEIN(this.institutionEIN)
                .contactName(this.contactName)
                .contactTitle(this.contactTitle)
                .contactPhone(this.contactPhone)
                .contactEmail(this.contactEmail)
                .jurisdictionCode(this.jurisdictionCode)
                .filingDate(LocalDateTime.now())
                .incidentDate(this.incidentDate)
                .createdAt(LocalDateTime.now())
                .reportNumber(generateReportNumber())
                .subjects(new ArrayList<>(this.subjects))
                .involvedParties(new ArrayList<>(this.involvedParties))
                .regulatoryFlags(new HashMap<>(this.regulatoryFlags))
                .metadata(new HashMap<>(this.metadata))
                .build();
        
        return amendment;
    }
}