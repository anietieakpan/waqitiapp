package com.waqiti.saga.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * International Transfer Request DTO
 *
 * Contains all information required for international money transfer saga
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternationalTransferRequest {

    // Transfer identification
    private UUID transferId;
    private String correlationId;
    private LocalDateTime requestedAt;

    // Sender information
    private UUID senderId;
    private String senderName;
    private String senderAddress;
    private String senderCity;
    private String senderCountry;
    private String senderPostalCode;
    private String senderPhone;
    private String senderEmail;
    private String senderAccountNumber;

    // Recipient information
    private UUID recipientId;
    private String recipientName;
    private String recipientAddress;
    private String recipientCity;
    private String recipientCountry;
    private String recipientPostalCode;
    private String recipientPhone;
    private String recipientEmail;
    private String recipientAccountNumber;
    private String recipientBankName;
    private String recipientBankAddress;

    // Banking information
    private String swiftCode;  // BIC/SWIFT code
    private String iban;  // International Bank Account Number
    private String routingNumber;  // For US transfers
    private String sortCode;  // For UK transfers
    private String bsbCode;  // For Australian transfers
    private String ifscCode;  // For Indian transfers

    // Amount information
    private BigDecimal sourceAmount;  // Amount in sender's currency
    private String sourceCurrency;  // e.g., USD
    private BigDecimal destinationAmount;  // Amount in recipient's currency
    private String destinationCurrency;  // e.g., EUR
    private BigDecimal exchangeRate;  // FX rate to be locked
    private BigDecimal fees;  // Total fees
    private BigDecimal fxSpread;  // Foreign exchange spread

    // Transfer details
    private String purpose;  // Purpose of transfer (required for compliance)
    private String transferReason;  // Reason code (salary, family support, business, etc.)
    private String paymentMethod;  // SWIFT, SEPA, ACH International, etc.
    private String deliveryMethod;  // Bank account, cash pickup, mobile wallet
    private String urgency;  // STANDARD, EXPRESS, URGENT
    private LocalDateTime requestedDeliveryDate;

    // Compliance information
    private String sourceOfFunds;  // Where money came from
    private String occupation;  // Sender's occupation
    private String relationshipToRecipient;  // Family, business partner, etc.
    private Boolean highRiskCountry;  // Is destination high-risk?
    private Boolean pep;  // Is sender/recipient a Politically Exposed Person?
    private String regulatoryPurposeCode;  // For regulatory reporting

    // Fraud detection
    private String ipAddress;
    private String deviceId;
    private String sessionId;
    private String userAgent;
    private Double fraudScore;  // Pre-calculated fraud risk score

    // Metadata
    private Map<String, Object> metadata;  // Additional context
    private String idempotencyKey;  // For duplicate prevention
}
