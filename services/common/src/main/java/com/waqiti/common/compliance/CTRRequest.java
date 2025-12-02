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
 * Currency Transaction Report (CTR) request object
 * 
 * Specialized request for creating CTR filings with FinCEN for currency
 * transactions exceeding regulatory thresholds (typically $10,000 USD).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CTRRequest {

    /**
     * CTR filing information
     */
    private CTRFilingInfo filingInfo;

    /**
     * Financial institution information
     */
    private FinancialInstitutionInfo financialInstitution;

    /**
     * Person(s) conducting the transaction
     */
    private List<PersonConductingTransaction> personsConducting;

    /**
     * Person(s) on whose behalf transaction was conducted
     */
    private List<PersonOnBehalfOf> personsOnBehalfOf;

    /**
     * Transaction details
     */
    private TransactionDetails transactionDetails;

    /**
     * Currency transaction information
     */
    private List<CurrencyTransactionInfo> currencyTransactions;

    /**
     * Account information
     */
    private List<AccountInfo> affectedAccounts;

    /**
     * Supporting documentation
     */
    private List<SupportingDocumentInfo> supportingDocuments;

    /**
     * Contact information for the filing
     */
    private ContactInfo contactInfo;

    /**
     * CTR filing information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CTRFilingInfo {
        private String ctrNumber;
        private LocalDateTime filingDate;
        private String filingType; // Initial, Corrected, Amendment
        private String priorCtrNumber; // For corrections/amendments
        private LocalDateTime transactionDate;
        private String multipleTransactions; // Yes/No
        private LocalDateTime aggregationPeriodStart;
        private LocalDateTime aggregationPeriodEnd;
        private String reportingBasis; // Regular, Aggregated, Both
        private boolean exemptionApplicable;
        private String exemptionType;
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
        private String branchLocation;
        private String branchNumber;
    }

    /**
     * Person conducting transaction
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonConductingTransaction {
        private String personNumber; // For multiple persons
        private IndividualInfo individual;
        private EntityInfo entity;
        private String conductorType; // Individual, Entity, Both
        private String accountRelationship;
        private List<IdentificationInfo> identifications;
        private AddressInfo address;
        private PhoneInfo phoneNumber;
        private String occupation;
        private String businessPurpose;
        private boolean verificationPerformed;
        private String verificationMethod;
    }

    /**
     * Person on whose behalf transaction was conducted
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonOnBehalfOf {
        private String personNumber; // For multiple persons
        private IndividualInfo individual;
        private EntityInfo entity;
        private String behalfType; // Individual, Entity
        private String relationshipToConductor;
        private List<IdentificationInfo> identifications;
        private AddressInfo address;
        private PhoneInfo phoneNumber;
        private String accountNumber;
        private boolean beneficialOwner;
    }

    /**
     * Individual information
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
        private String gender;
        private String ssn; // Social Security Number (if available)
        private String itin; // Individual Taxpayer Identification Number
        private String occupation;
        private String employer;
        private String countryOfCitizenship;
        private String countryOfResidence;
    }

    /**
     * Entity information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityInfo {
        private String entityName;
        private String entityType; // Corporation, Partnership, LLC, etc.
        private String ein; // Employer Identification Number
        private String dbaName;
        private String incorporationState;
        private String incorporationCountry;
        private LocalDateTime incorporationDate;
        private String businessType;
        private String naicsCode; // North American Industry Classification System
        private List<IndividualInfo> authorizedPersons;
    }

    /**
     * Transaction details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDetails {
        private BigDecimal totalCashIn;
        private BigDecimal totalCashOut;
        private BigDecimal totalTransactionAmount;
        private String primaryCurrency;
        private LocalDateTime transactionDate;
        private LocalDateTime postingDate;
        private String transactionLocation;
        private String transactionType; // Deposit, Withdrawal, Exchange, etc.
        private List<String> instrumentTypes; // Cash, Check, Wire, etc.
        private boolean aggregatedTransaction;
        private int numberOfTransactions;
        private String purposeOfTransaction;
    }

    /**
     * Currency transaction information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyTransactionInfo {
        private String transactionId;
        private String transactionType; // Cash In, Cash Out, Foreign Exchange
        private BigDecimal amount;
        private String currency;
        private String denominationBreakdown;
        private String instrumentType; // Currency, Cashier's Check, Money Order, etc.
        private String instrumentNumber;
        private String issuingInstitution;
        private LocalDateTime transactionTime;
        private String tellerNumber;
        private String atmNumber;
        private Map<String, Object> additionalDetails;
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
        private String accountTitle;
        private LocalDateTime openDate;
        private BigDecimal currentBalance;
        private String currency;
        private List<String> accountHolders;
        private String branchLocation;
        private boolean primaryAccount;
    }

    /**
     * Identification information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdentificationInfo {
        private String identificationType; // Driver's License, Passport, State ID, etc.
        private String identificationNumber;
        private String issuingAuthority;
        private String issuingState;
        private String issuingCountry;
        private LocalDateTime issuanceDate;
        private LocalDateTime expirationDate;
        private boolean verified;
        private String verificationMethod;
    }

    /**
     * Address information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressInfo {
        private String streetAddress;
        private String city;
        private String state;
        private String zipCode;
        private String country;
        private String addressType; // Residential, Business, Mailing
        private boolean verified;
    }

    /**
     * Phone information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhoneInfo {
        private String phoneNumber;
        private String phoneType; // Mobile, Home, Business
        private String extension;
        private String countryCode;
        private boolean verified;
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
        private String alternateContact;
        private String alternatePhone;
    }

    // Validation methods

    /**
     * Validate the CTR request
     */
    public CTRValidation validate() {
        CTRValidation validation = new CTRValidation();

        // Required fields validation
        if (filingInfo == null) {
            validation.addError("filingInfo", "Filing information is required");
        } else {
            if (filingInfo.getFilingDate() == null) {
                validation.addError("filingInfo.filingDate", "Filing date is required");
            }
            if (filingInfo.getTransactionDate() == null) {
                validation.addError("filingInfo.transactionDate", "Transaction date is required");
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

        if (personsConducting == null || personsConducting.isEmpty()) {
            validation.addError("personsConducting", "At least one person conducting transaction is required");
        }

        if (transactionDetails == null) {
            validation.addError("transactionDetails", "Transaction details are required");
        } else {
            // Validate transaction amount threshold
            BigDecimal threshold = new BigDecimal("10000");
            if (transactionDetails.getTotalTransactionAmount() == null) {
                validation.addError("transactionDetails.amount", "Total transaction amount is required");
            } else if (transactionDetails.getTotalTransactionAmount().compareTo(threshold) < 0) {
                validation.addWarning("transactionDetails.amount", 
                    "Transaction amount is below $10,000 threshold");
            }
        }

        if (currencyTransactions == null || currencyTransactions.isEmpty()) {
            validation.addError("currencyTransactions", "At least one currency transaction is required");
        }

        // Business logic validation
        if (filingInfo != null && filingInfo.getTransactionDate() != null && 
            filingInfo.getTransactionDate().isAfter(LocalDateTime.now())) {
            validation.addError("filingInfo.transactionDate", "Transaction date cannot be in the future");
        }

        // Validate aggregation period
        if (filingInfo != null && "Aggregated".equals(filingInfo.getReportingBasis())) {
            if (filingInfo.getAggregationPeriodStart() == null || filingInfo.getAggregationPeriodEnd() == null) {
                validation.addError("filingInfo.aggregationPeriod", 
                    "Aggregation period required for aggregated reporting");
            }
        }

        // Validate persons conducting transactions
        if (personsConducting != null) {
            for (int i = 0; i < personsConducting.size(); i++) {
                PersonConductingTransaction person = personsConducting.get(i);
                if (person.getIndividual() == null && person.getEntity() == null) {
                    validation.addError("personsConducting[" + i + "]", 
                        "Either individual or entity information required");
                }
                if (person.getIdentifications() == null || person.getIdentifications().isEmpty()) {
                    validation.addError("personsConducting[" + i + "].identification", 
                        "At least one identification required");
                }
            }
        }

        // Validate cash in/out amounts
        if (transactionDetails != null) {
            BigDecimal cashIn = transactionDetails.getTotalCashIn() != null ? 
                transactionDetails.getTotalCashIn() : BigDecimal.ZERO;
            BigDecimal cashOut = transactionDetails.getTotalCashOut() != null ? 
                transactionDetails.getTotalCashOut() : BigDecimal.ZERO;
            BigDecimal total = transactionDetails.getTotalTransactionAmount() != null ? 
                transactionDetails.getTotalTransactionAmount() : BigDecimal.ZERO;
            
            if (!cashIn.add(cashOut).equals(total) && total.compareTo(BigDecimal.ZERO) > 0) {
                validation.addWarning("transactionDetails", 
                    "Cash in + Cash out does not equal total transaction amount");
            }
        }

        return validation;
    }

    /**
     * CTR validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CTRValidation {
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
     * Check if this is an aggregated transaction report
     */
    public boolean isAggregated() {
        return transactionDetails != null && 
               Boolean.TRUE.equals(transactionDetails.isAggregatedTransaction());
    }

    /**
     * Check if exemption applies
     */
    public boolean isExemptionApplicable() {
        return filingInfo != null && 
               Boolean.TRUE.equals(filingInfo.isExemptionApplicable());
    }

    /**
     * Get total number of persons involved
     */
    public int getTotalPersonsInvolved() {
        int count = 0;
        if (personsConducting != null) count += personsConducting.size();
        if (personsOnBehalfOf != null) count += personsOnBehalfOf.size();
        return count;
    }

    /**
     * Check if this is a correction or amendment
     */
    public boolean isCorrectionOrAmendment() {
        return filingInfo != null && 
               filingInfo.getFilingType() != null &&
               (filingInfo.getFilingType().contains("Correct") || 
                filingInfo.getFilingType().contains("Amendment"));
    }

    /**
     * Create basic CTR request
     */
    public static CTRRequest createBasic(String institutionName, String ein,
                                       String personName, BigDecimal amount,
                                       LocalDateTime transactionDate) {
        return CTRRequest.builder()
            .filingInfo(CTRFilingInfo.builder()
                .filingDate(LocalDateTime.now())
                .filingType("Initial")
                .transactionDate(transactionDate)
                .multipleTransactions("No")
                .reportingBasis("Regular")
                .build())
            .financialInstitution(FinancialInstitutionInfo.builder()
                .legalName(institutionName)
                .ein(ein)
                .build())
            .personsConducting(List.of(PersonConductingTransaction.builder()
                .individual(IndividualInfo.builder()
                    .firstName(personName.split(" ")[0])
                    .lastName(personName.split(" ").length > 1 ? personName.split(" ")[1] : "")
                    .build())
                .build()))
            .transactionDetails(TransactionDetails.builder()
                .totalTransactionAmount(amount)
                .totalCashIn(amount)
                .totalCashOut(BigDecimal.ZERO)
                .primaryCurrency("USD")
                .transactionDate(transactionDate)
                .transactionType("Deposit")
                .numberOfTransactions(1)
                .build())
            .currencyTransactions(List.of(CurrencyTransactionInfo.builder()
                .transactionType("Cash In")
                .amount(amount)
                .currency("USD")
                .instrumentType("Currency")
                .transactionTime(transactionDate)
                .build()))
            .build();
    }

    /**
     * Get transaction amount from transaction details
     */
    public BigDecimal getTransactionAmount() {
        if (transactionDetails != null) {
            return transactionDetails.getTotalTransactionAmount();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Get customer ID from the first person conducting transaction
     */
    public String getCustomerId() {
        if (personsConducting != null && !personsConducting.isEmpty()) {
            PersonConductingTransaction person = personsConducting.get(0);
            if (person.getIndividual() != null && person.getIndividual().getSsn() != null) {
                return person.getIndividual().getSsn();
            }
            if (person.getEntity() != null && person.getEntity().getEin() != null) {
                return person.getEntity().getEin();
            }
        }
        return null;
    }

    /**
     * Get transaction ID from the first currency transaction
     */
    public String getTransactionId() {
        if (currencyTransactions != null && !currencyTransactions.isEmpty()) {
            return currencyTransactions.get(0).getTransactionId();
        }
        if (filingInfo != null && filingInfo.getCtrNumber() != null) {
            return filingInfo.getCtrNumber();
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
}