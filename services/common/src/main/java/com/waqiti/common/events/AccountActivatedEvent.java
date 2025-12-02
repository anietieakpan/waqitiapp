package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when an account is activated.
 *
 * This event is published when:
 * - A pending account is activated after verification
 * - Account passes KYC requirements
 * - Manual activation by admin
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountActivatedEvent extends FinancialEvent {

    private UUID accountId;
    private String accountNumber;
    private String accountType;
    private String accountTier;  // BASIC, STANDARD, PREMIUM, VIP, PLATINUM
    private String activationMethod;  // AUTO, MANUAL, KYC_VERIFIED
    private String previousStatus;
    private String newStatus;
    private Instant activatedAt;
    private UUID activatedBy;  // User/Admin who activated
    private String activationReason;
    private String kycLevel;
    private String verificationLevel;  // BASIC, STANDARD, ENHANCED
    private Map<String, String> accountMetadata;
    private String ipAddress;
    private String userAgent;

    /**
     * Factory method to create account activated event
     */
    public static AccountActivatedEvent create(UUID accountId, String accountNumber, UUID userId,
                                                String accountType, String activationMethod) {
        return AccountActivatedEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACCOUNT_ACTIVATED")
            .eventCategory("ACCOUNT")
            .accountId(accountId)
            .accountNumber(accountNumber)
            .userId(userId)
            .accountType(accountType)
            .activationMethod(activationMethod)
            .previousStatus("PENDING")
            .newStatus("ACTIVE")
            .activatedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateType("Account")
            .aggregateId(accountId)
            .build();
    }

    /**
     * Check if activation was automatic
     */
    public boolean isAutoActivated() {
        return "AUTO".equalsIgnoreCase(activationMethod);
    }

    /**
     * Check if activation was manual
     */
    public boolean isManualActivated() {
        return "MANUAL".equalsIgnoreCase(activationMethod);
    }

    /**
     * Check if activation was via KYC verification
     */
    public boolean isKycActivated() {
        return "KYC_VERIFIED".equalsIgnoreCase(activationMethod);
    }
}
