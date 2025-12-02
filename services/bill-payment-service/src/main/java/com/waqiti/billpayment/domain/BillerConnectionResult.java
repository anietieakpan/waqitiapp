package com.waqiti.billpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain object representing the result of establishing a connection to a biller
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillerConnectionResult {

    /**
     * Whether the connection was successful
     */
    private boolean success;

    /**
     * Connection ID if successful
     */
    private UUID connectionId;

    /**
     * External connection ID from biller system
     */
    private String externalConnectionId;

    /**
     * Account number
     */
    private String accountNumber;

    /**
     * Account name
     */
    private String accountName;

    /**
     * Connection status
     */
    private String status;

    /**
     * Error message if connection failed
     */
    private String errorMessage;

    /**
     * Error code if connection failed
     */
    private String errorCode;

    /**
     * Whether the connection requires additional authentication
     */
    private Boolean requiresAdditionalAuth;

    /**
     * Authentication URL if additional auth is required
     */
    private String authUrl;

    /**
     * Number of bills imported during connection
     */
    private Integer billsImportedCount;

    /**
     * List of imported bill IDs
     */
    private List<UUID> importedBillIds;

    /**
     * Connection timestamp
     */
    private LocalDateTime connectionTimestamp;

    /**
     * Whether auto-import is enabled
     */
    private Boolean autoImportEnabled;

    /**
     * Whether e-bill delivery is enabled
     */
    private Boolean ebillEnabled;

    /**
     * Next scheduled import time
     */
    private LocalDateTime nextImportAt;

    /**
     * Helper method to create a successful connection result
     */
    public static BillerConnectionResult success(
            UUID connectionId,
            String externalConnectionId,
            String accountNumber,
            String accountName) {

        return BillerConnectionResult.builder()
                .success(true)
                .connectionId(connectionId)
                .externalConnectionId(externalConnectionId)
                .accountNumber(accountNumber)
                .accountName(accountName)
                .status("ACTIVE")
                .connectionTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Helper method to create a failed connection result
     */
    public static BillerConnectionResult failure(String errorCode, String errorMessage) {
        return BillerConnectionResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .status("FAILED")
                .connectionTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Helper method to create a result requiring additional auth
     */
    public static BillerConnectionResult requiresAuth(String authUrl) {
        return BillerConnectionResult.builder()
                .success(false)
                .requiresAdditionalAuth(true)
                .authUrl(authUrl)
                .status("PENDING_AUTH")
                .connectionTimestamp(LocalDateTime.now())
                .build();
    }
}
