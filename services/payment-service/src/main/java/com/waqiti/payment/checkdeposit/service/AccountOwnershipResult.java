package com.waqiti.payment.checkdeposit.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Account Ownership Validation Result
 *
 * Contains the results of an account ownership validation including:
 * - Ownership verification status
 * - Account owner details (name, email, phone, address)
 * - Number of account owners
 * - Verification method and timestamp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOwnershipResult {

    /**
     * Whether ownership was successfully verified
     */
    private boolean verified;

    /**
     * Plaid account ID
     */
    private String accountId;

    /**
     * Primary account owner name
     */
    private String ownerName;

    /**
     * Primary account owner email
     */
    private String ownerEmail;

    /**
     * Primary account owner phone number
     */
    private String ownerPhone;

    /**
     * Primary account owner address
     */
    private String ownerAddress;

    /**
     * Number of account owners
     */
    private int numberOfOwners;

    /**
     * Verification method used (PLAID_IDENTITY)
     */
    private String verificationMethod;

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
}
