package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * International Transfer Response DTO
 *
 * Contains comprehensive international money transfer results including
 * compliance checks, currency conversion, routing details, and tracking information.
 *
 * COMPLIANCE RELEVANCE:
 * - AML/KYC: Anti-money laundering checks
 * - OFAC: Sanctions screening
 * - SWIFT: International banking standards
 * - Local Regulations: Country-specific transfer requirements
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternationalTransferResponse {

    /**
     * Unique transfer identifier
     */
    @NotNull
    private UUID transferId;

    /**
     * User ID initiating the transfer
     */
    @NotNull
    private UUID userId;

    /**
     * Transfer status
     * Values: INITIATED, COMPLIANCE_CHECK, PENDING_APPROVAL, PROCESSING,
     *         IN_TRANSIT, COMPLETED, FAILED, CANCELLED, RETURNED
     */
    @NotNull
    private String status;

    /**
     * Source country code (ISO 3166-1 alpha-2)
     */
    @NotNull
    private String fromCountry;

    /**
     * Destination country code (ISO 3166-1 alpha-2)
     */
    @NotNull
    private String toCountry;

    /**
     * Source currency (ISO 4217)
     */
    @NotNull
    private String sourceCurrency;

    /**
     * Destination currency (ISO 4217)
     */
    @NotNull
    private String destinationCurrency;

    /**
     * Source amount (in source currency)
     */
    @NotNull
    @Positive
    private BigDecimal sourceAmount;

    /**
     * Destination amount (in destination currency)
     */
    @NotNull
    @Positive
    private BigDecimal destinationAmount;

    /**
     * Exchange rate applied
     */
    @NotNull
    @Positive
    private BigDecimal exchangeRate;

    /**
     * Exchange rate timestamp
     */
    private LocalDateTime exchangeRateTimestamp;

    /**
     * Exchange rate markup percentage
     */
    private BigDecimal exchangeRateMarkup;

    /**
     * Transfer fee
     */
    @Positive
    private BigDecimal transferFee;

    /**
     * Currency conversion fee
     */
    private BigDecimal currencyConversionFee;

    /**
     * Intermediary bank fees
     */
    private BigDecimal intermediaryBankFees;

    /**
     * Total fees
     */
    @Positive
    private BigDecimal totalFees;

    /**
     * Net amount debited from sender
     */
    @NotNull
    @Positive
    private BigDecimal netDebitAmount;

    /**
     * Net amount credited to recipient
     */
    @NotNull
    @Positive
    private BigDecimal netCreditAmount;

    /**
     * Transfer method
     * Values: SWIFT, SEPA, WIRE, ACH_INTERNATIONAL, LOCAL_BANK_TRANSFER
     */
    @NotNull
    private String transferMethod;

    /**
     * Transfer type
     * Values: PERSON_TO_PERSON, PERSON_TO_BUSINESS, BUSINESS_TO_PERSON, BUSINESS_TO_BUSINESS
     */
    private String transferType;

    /**
     * Transfer purpose
     */
    private String transferPurpose;

    /**
     * Purpose code (for regulatory reporting)
     */
    private String purposeCode;

    /**
     * Sender details
     */
    private TransferParty senderDetails;

    /**
     * Recipient details
     */
    private TransferParty recipientDetails;

    /**
     * Beneficiary bank details
     */
    private BankDetails beneficiaryBankDetails;

    /**
     * Intermediary bank details
     */
    private List<BankDetails> intermediaryBanks;

    /**
     * SWIFT message type
     */
    private String swiftMessageType;

    /**
     * SWIFT reference number
     */
    private String swiftReference;

    /**
     * Tracking number
     */
    private String trackingNumber;

    /**
     * Estimated delivery time
     */
    private LocalDateTime estimatedDeliveryTime;

    /**
     * Actual delivery time
     */
    private LocalDateTime actualDeliveryTime;

    /**
     * Compliance status
     * Values: PENDING, APPROVED, REJECTED, UNDER_REVIEW, CLEARED
     */
    @NotNull
    private String complianceStatus;

    /**
     * AML check result
     */
    private String amlCheckResult;

    /**
     * Sanctions screening result
     */
    private String sanctionsScreeningResult;

    /**
     * KYC verification status
     */
    private String kycVerificationStatus;

    /**
     * Risk score (0-100)
     */
    private int riskScore;

    /**
     * Risk level
     * Values: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String riskLevel;

    /**
     * Compliance flags
     */
    private List<String> complianceFlags;

    /**
     * Requires manual review flag
     */
    private boolean requiresManualReview;

    /**
     * Reviewed by (if manual review performed)
     */
    private UUID reviewedBy;

    /**
     * Review timestamp
     */
    private LocalDateTime reviewedAt;

    /**
     * Review notes
     */
    private String reviewNotes;

    /**
     * Regulatory reporting required
     */
    private boolean regulatoryReportingRequired;

    /**
     * Regulatory reports submitted
     */
    private List<String> regulatoryReportsSubmitted;

    /**
     * Source of funds
     */
    private String sourceOfFunds;

    /**
     * Supporting documents
     */
    private List<String> supportingDocuments;

    /**
     * Transfer initiated timestamp
     */
    @NotNull
    private LocalDateTime initiatedAt;

    /**
     * Transfer processed timestamp
     */
    private LocalDateTime processedAt;

    /**
     * Transfer completed timestamp
     */
    private LocalDateTime completedAt;

    /**
     * Processing duration (ms)
     */
    private long processingDurationMs;

    /**
     * Error code if transfer failed
     */
    private String errorCode;

    /**
     * Error message if transfer failed
     */
    private String errorMessage;

    /**
     * Failure reason
     */
    private String failureReason;

    /**
     * Retry count
     */
    private int retryCount;

    /**
     * Retryable flag
     */
    private boolean retryable;

    /**
     * Notification sent flag
     */
    private boolean notificationSent;

    /**
     * Recipient notified flag
     */
    private boolean recipientNotified;

    /**
     * Confirmation code
     */
    private String confirmationCode;

    /**
     * Reference text for recipient
     */
    private String referenceText;

    /**
     * Notes or additional information
     */
    private String notes;

    /**
     * Audit trail reference
     */
    private UUID auditTrailId;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Transfer Party nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferParty {
        private String name;
        private String accountNumber;
        private String accountType;
        private String address;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String phoneNumber;
        private String email;
        private String identificationNumber;
        private String identificationType;
    }

    /**
     * Bank Details nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankDetails {
        private String bankName;
        private String bankCode;
        private String swiftCode;
        private String iban;
        private String routingNumber;
        private String branchCode;
        private String bankAddress;
        private String bankCity;
        private String bankCountry;
    }
}
