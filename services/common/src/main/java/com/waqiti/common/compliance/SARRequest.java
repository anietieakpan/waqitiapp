package com.waqiti.common.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Suspicious Activity Report (SAR) request object
 * 
 * Specialized request for creating SAR filings with FinCEN and other
 * regulatory authorities when suspicious financial activities are detected.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SARRequest {

    /**
     * SAR filing information
     */
    private SARFilingInfo filingInfo;

    /**
     * Financial institution information
     */
    private FinancialInstitutionInfo financialInstitution;

    /**
     * Suspect information
     */
    private List<SuspectInfo> suspects;

    /**
     * Suspicious activity details
     */
    private SuspiciousActivityInfo suspiciousActivity;

    /**
     * Suspicious activity details (DTO compatibility field)
     */
    private String suspiciousActivityDetails;

    /**
     * Transaction information
     */
    private List<SuspiciousTransactionInfo> transactions;

    /**
     * Account information
     */
    private List<AccountInfo> accounts;

    /**
     * Supporting documentation
     */
    private List<SupportingDocumentInfo> supportingDocuments;

    /**
     * Law enforcement contact information if applicable
     */
    private LawEnforcementInfo lawEnforcement;

    /**
     * Additional narrative information
     */
    private NarrativeInfo narrative;

    /**
     * Contact information for the filing
     */
    private ContactInfo contactInfo;

    /**
     * SAR filing information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SARFilingInfo {
        private String sarNumber;
        private LocalDateTime filingDate;
        private String filingType; // Initial, Corrected, Amendment, etc.
        private String priorSarNumber; // For corrections/amendments
        private boolean discretionaryFiling;
        private String filingReason;
        private String jurisdictionCode;
        private boolean lawEnforcementRequested;
        private LocalDateTime incidentDate;
        private LocalDateTime detectionDate;
    }

    /**
     * Financial institution information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialInstitutionInfo {
        private String legalName;
        private String dbaName;
        private String ein; // Employer Identification Number
        private String rssdId; // RSSD ID number
        private String primaryRegulator;
        private AddressInfo address;
        private ContactInfo primaryContact;
        private String institutionType;
        private String charterNumber;
        private String regulatoryIdNumber;
    }

    /**
     * Suspect information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspectInfo {
        private String suspectNumber; // For multiple suspects
        private IndividualInfo individual;
        private EntityInfo entity;
        private String suspectType; // Individual, Entity, Unknown
        private String roleInActivity;
        private String relationshipToInstitution;
        private List<IdentificationInfo> identifications;
        private List<AddressInfo> addresses;
        private List<PhoneInfo> phoneNumbers;
        private List<String> aliases;
        private String riskLevel;
        private String suspicionBasis;
    }

    /**
     * Individual suspect information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndividualInfo {
        private String lastName;
        private String firstName;
        private String middleName;
        private String suffix;
        private LocalDateTime dateOfBirth;
        private String placeOfBirth;
        private String gender;
        private String occupation;
        private String employer;
        private String nationality;
        private String countryOfResidence;
    }

    /**
     * Entity suspect information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityInfo {
        private String entityName;
        private String entityType; // Corporation, Partnership, LLC, etc.
        private String ein;
        private String incorporationJurisdiction;
        private LocalDateTime incorporationDate;
        private String businessPurpose;
        private List<IndividualInfo> authorizedSigners;
        private List<IndividualInfo> beneficialOwners;
    }

    /**
     * Identification information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdentificationInfo {
        private String identificationType; // SSN, Driver's License, Passport, etc.
        private String identificationNumber;
        private String issuingAuthority;
        private String issuingCountry;
        private LocalDateTime issuanceDate;
        private LocalDateTime expirationDate;
    }

    /**
     * Address information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressInfo {
        private String addressType; // Residence, Business, Mailing, etc.
        private String streetAddress;
        private String city;
        private String state;
        private String zipCode;
        private String country;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
    }

    /**
     * Phone information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhoneInfo {
        private String phoneType; // Mobile, Home, Business, etc.
        private String phoneNumber;
        private String extension;
        private String countryCode;
    }

    /**
     * Suspicious activity information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousActivityInfo {
        private List<String> suspiciousActivityTypes;
        private String primaryActivityType;
        private String activityDescription;
        private LocalDateTime activityBeganDate;
        private LocalDateTime activityEndDate;
        private BigDecimal totalSuspiciousAmount;
        private String currency;
        private List<String> instrumentTypes;
        private List<String> locationsOfActivity;
        private String methodOfOperation;
        private boolean continuingActivity;
        private String lossMitigationActions;
        private BigDecimal institutionLoss;
        private BigDecimal customerLoss;
    }

    /**
     * Suspicious transaction information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousTransactionInfo {
        private String transactionId;
        private LocalDateTime transactionDate;
        private BigDecimal transactionAmount;
        private String currency;
        private String transactionType;
        private String instrumentType;
        private String fromLocation;
        private String toLocation;
        private String fromAccount;
        private String toAccount;
        private String correspondent;
        private String beneficiary;
        private String purpose;
        private String suspiciousIndicators;
        private Map<String, Object> additionalInfo;
    }

    /**
     * Account information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountInfo {
        private String accountNumber;
        private String accountType;
        private LocalDateTime openDate;
        private LocalDateTime closeDate;
        private String branchLocation;
        private BigDecimal currentBalance;
        private String currency;
        private List<String> accountHolders;
        private List<String> authorizedSigners;
        private String accountStatus;
    }

    /**
     * Supporting document information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportingDocumentInfo {
        private String documentId;
        private String documentType;
        private String documentDescription;
        private String fileName;
        private LocalDateTime documentDate;
        private boolean includedWithFiling;
        private String retentionLocation;
    }

    /**
     * Law enforcement information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LawEnforcementInfo {
        private boolean contactMade;
        private String agencyName;
        private String contactPerson;
        private String contactPhone;
        private String contactEmail;
        private LocalDateTime contactDate;
        private String caseNumber;
        private String investigationStatus;
        private String additionalInfo;
    }

    /**
     * Narrative information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeInfo {
        private String executiveSummary;
        private String detailedNarrative;
        private String timelineOfEvents;
        private String redFlags;
        private String mitigatingFactors;
        private String investigationSteps;
        private String conclusion;
        private String recommendations;
    }

    /**
     * Contact information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactInfo {
        private String contactName;
        private String title;
        private String department;
        private String phone;
        private String email;
        private AddressInfo address;
        private LocalDateTime lastContactDate;
        private String alternateContact;
        private String alternatePhone;
        private String alternateEmail;
    }

    // Validation methods

    /**
     * Validate the SAR request
     */
    public SARValidation validate() {
        SARValidation validation = new SARValidation();

        // Required fields validation
        if (filingInfo == null) {
            validation.addError("filingInfo", "Filing information is required");
        } else {
            if (filingInfo.getFilingDate() == null) {
                validation.addError("filingInfo.filingDate", "Filing date is required");
            }
            if (filingInfo.getIncidentDate() == null) {
                validation.addError("filingInfo.incidentDate", "Incident date is required");
            }
        }

        if (financialInstitution == null) {
            validation.addError("financialInstitution", "Financial institution information is required");
        } else {
            if (financialInstitution.getLegalName() == null || financialInstitution.getLegalName().trim().isEmpty()) {
                validation.addError("financialInstitution.legalName", "Institution legal name is required");
            }
            if (financialInstitution.getEin() == null || financialInstitution.getEin().trim().isEmpty()) {
                validation.addError("financialInstitution.ein", "Institution EIN is required");
            }
        }

        if (suspects == null || suspects.isEmpty()) {
            validation.addError("suspects", "At least one suspect is required");
        }

        if (suspiciousActivity == null) {
            validation.addError("suspiciousActivity", "Suspicious activity information is required");
        } else {
            if (suspiciousActivity.getSuspiciousActivityTypes() == null || 
                suspiciousActivity.getSuspiciousActivityTypes().isEmpty()) {
                validation.addError("suspiciousActivity.types", "At least one suspicious activity type is required");
            }
            if (suspiciousActivity.getTotalSuspiciousAmount() == null || 
                suspiciousActivity.getTotalSuspiciousAmount().compareTo(BigDecimal.ZERO) <= 0) {
                validation.addError("suspiciousActivity.amount", "Total suspicious amount must be greater than zero");
            }
        }

        if (narrative == null) {
            validation.addError("narrative", "Narrative information is required");
        } else {
            if (narrative.getDetailedNarrative() == null || narrative.getDetailedNarrative().trim().isEmpty()) {
                validation.addError("narrative.detailedNarrative", "Detailed narrative is required");
            }
        }

        if (contactInfo == null) {
            validation.addError("contactInfo", "Contact information is required");
        } else {
            if (contactInfo.getContactName() == null || contactInfo.getContactName().trim().isEmpty()) {
                validation.addError("contactInfo.contactName", "Contact name is required");
            }
            if (contactInfo.getPhone() == null || contactInfo.getPhone().trim().isEmpty()) {
                validation.addError("contactInfo.phone", "Contact phone is required");
            }
        }

        // Business logic validation
        if (filingInfo != null && filingInfo.getIncidentDate() != null && 
            filingInfo.getIncidentDate().isAfter(LocalDateTime.now())) {
            validation.addError("filingInfo.incidentDate", "Incident date cannot be in the future");
        }

        if (filingInfo != null && filingInfo.getDetectionDate() != null && 
            filingInfo.getIncidentDate() != null &&
            filingInfo.getDetectionDate().isBefore(filingInfo.getIncidentDate())) {
            validation.addWarning("filingInfo.detectionDate", "Detection date is before incident date");
        }

        // Validate suspects
        if (suspects != null) {
            for (int i = 0; i < suspects.size(); i++) {
                SuspectInfo suspect = suspects.get(i);
                if (suspect.getIndividual() == null && suspect.getEntity() == null) {
                    validation.addError("suspects[" + i + "]", "Either individual or entity information required");
                }
            }
        }

        return validation;
    }

    /**
     * SAR validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SARValidation {
        @Builder.Default
        private List<ValidationError> errors = new java.util.ArrayList<>();
        @Builder.Default
        private List<ValidationWarning> warnings = new java.util.ArrayList<>();

        public void addError(String field, String message) {
            errors.add(new ValidationError(field, message));
        }

        public void addWarning(String field, String message) {
            warnings.add(new ValidationWarning(field, message));
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Validation error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String message;
    }

    /**
     * Validation warning
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {
        private String field;
        private String message;
    }

    // Utility methods

    /**
     * Calculate total transaction amount
     */
    public BigDecimal getTotalTransactionAmount() {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        
        return transactions.stream()
            .filter(t -> t.getTransactionAmount() != null)
            .map(SuspiciousTransactionInfo::getTransactionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get primary suspect
     */
    public SuspectInfo getPrimarySuspect() {
        if (suspects == null || suspects.isEmpty()) return null;
        return suspects.get(0);
    }

    /**
     * Check if this is a correction or amendment
     */
    public boolean isCorrectionOrAmendment() {
        return filingInfo != null && 
               (filingInfo.getFilingType() != null) &&
               (filingInfo.getFilingType().contains("Correct") || 
                filingInfo.getFilingType().contains("Amendment"));
    }

    /**
     * Check if law enforcement has been contacted
     */
    public boolean isLawEnforcementContacted() {
        return lawEnforcement != null && 
               Boolean.TRUE.equals(lawEnforcement.isContactMade());
    }

    /**
     * Create basic SAR request
     */
    public static SARRequest createBasic(String institutionName, String ein, String suspectName,
                                       String activityType, BigDecimal amount, String narrative) {
        return SARRequest.builder()
            .filingInfo(SARFilingInfo.builder()
                .filingDate(LocalDateTime.now())
                .filingType("Initial")
                .incidentDate(LocalDateTime.now().minusDays(1))
                .detectionDate(LocalDateTime.now())
                .build())
            .financialInstitution(FinancialInstitutionInfo.builder()
                .legalName(institutionName)
                .ein(ein)
                .build())
            .suspects(List.of(SuspectInfo.builder()
                .individual(IndividualInfo.builder()
                    .firstName(suspectName.split(" ")[0])
                    .lastName(suspectName.split(" ").length > 1 ? suspectName.split(" ")[1] : "")
                    .build())
                .build()))
            .suspiciousActivity(SuspiciousActivityInfo.builder()
                .suspiciousActivityTypes(List.of(activityType))
                .totalSuspiciousAmount(amount)
                .currency("USD")
                .build())
            .narrative(NarrativeInfo.builder()
                .detailedNarrative(narrative)
                .build())
            .build();
    }

    /**
     * Get transaction ID from the first transaction if available
     */
    public String getTransactionId() {
        if (transactions != null && !transactions.isEmpty()) {
            return transactions.get(0).getTransactionId();
        }
        return null;
    }

    /**
     * Get generated by from contact info
     */
    public String getGeneratedBy() {
        if (contactInfo != null) {
            return contactInfo.getContactName();
        }
        return "System";
    }

    /**
     * Calculate risk score based on suspicious activity
     */
    public BigDecimal getRiskScore() {
        if (suspiciousActivity == null) {
            return BigDecimal.ZERO;
        }

        // Base risk score calculation
        BigDecimal baseScore = BigDecimal.valueOf(50); // Base 50

        // Add points for activity types
        if (suspiciousActivity.getSuspiciousActivityTypes() != null) {
            int activityCount = suspiciousActivity.getSuspiciousActivityTypes().size();
            baseScore = baseScore.add(BigDecimal.valueOf(activityCount * 10));
        }

        // Add points for high amounts
        if (suspiciousActivity.getTotalSuspiciousAmount() != null) {
            BigDecimal amount = suspiciousActivity.getTotalSuspiciousAmount();
            if (amount.compareTo(BigDecimal.valueOf(100000)) > 0) {
                baseScore = baseScore.add(BigDecimal.valueOf(20));
            } else if (amount.compareTo(BigDecimal.valueOf(50000)) > 0) {
                baseScore = baseScore.add(BigDecimal.valueOf(10));
            }
        }

        // Cap at 100
        return baseScore.min(BigDecimal.valueOf(100));
    }
}