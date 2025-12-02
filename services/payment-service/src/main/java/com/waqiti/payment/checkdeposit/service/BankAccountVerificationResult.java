package com.waqiti.payment.checkdeposit.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Bank Account Verification Result
 *
 * Contains the results of a bank account verification operation including:
 * - Verification status (verified/failed)
 * - Account details (type, subtype, masked number)
 * - Institution information
 * - Routing number
 * - Verification method used
 * - Error details if verification failed
 * - Operation tracking ID
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountVerificationResult {

    /**
     * Whether the account was successfully verified
     */
    private boolean verified;

    /**
     * Plaid account ID
     */
    private String accountId;

    /**
     * Account name/nickname
     */
    private String accountName;

    /**
     * Account type (DEPOSITORY, CREDIT, LOAN, INVESTMENT)
     */
    private String accountType;

    /**
     * Account subtype (CHECKING, SAVINGS, etc.)
     */
    private String accountSubtype;

    /**
     * Bank routing number
     */
    private String routingNumber;

    /**
     * Masked account number (last 4 digits)
     */
    private String accountNumberMasked;

    /**
     * Plaid institution ID
     */
    private String institutionId;

    /**
     * Bank/institution name
     */
    private String institutionName;

    /**
     * Verification method used (PLAID_AUTH, MICRO_DEPOSITS, INSTANT_VERIFICATION)
     */
    private String verificationMethod;

    /**
     * Verification status (VERIFIED, PENDING, FAILED, SERVICE_UNAVAILABLE)
     */
    private String verificationStatus;

    /**
     * Timestamp when verification was completed
     */
    private Instant verifiedAt;

    /**
     * Error code if verification failed
     */
    private String errorCode;

    /**
     * Error message if verification failed
     */
    private String errorMessage;

    /**
     * Unique operation ID for tracking
     */
    private String operationId;

    /**
     * Additional metadata
     */
    private String metadata;
}
